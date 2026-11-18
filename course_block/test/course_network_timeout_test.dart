import 'dart:async';
import 'dart:typed_data';

import 'package:course_block/core/services/course_service.dart';
import 'package:course_block/core/theme/app_theme.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Adapter implements HttpClientAdapter {
  _Adapter(this.respond);

  final Future<ResponseBody> Function(RequestOptions, Future<void>?) respond;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) => respond(options, cancelFuture);

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'ug_cookies': 'fake-session',
      'grad_cookies': 'fake-session',
      'active_login_system': 'ug',
    });
  });

  test('production phase limits bound connect, send and idle receive', () {
    final dio = Dio();
    CourseService(dio: dio);
    expect(dio.options.connectTimeout, const Duration(seconds: 15));
    expect(dio.options.sendTimeout, const Duration(seconds: 15));
    expect(dio.options.receiveTimeout, const Duration(seconds: 20));
  });

  test(
    'undergraduate never-completing request times out and cancels',
    () async {
      final started = Completer<void>();
      final cancelled = Completer<void>();
      final dio = Dio();
      dio.httpClientAdapter = _Adapter((_, cancelFuture) {
        started.complete();
        cancelFuture?.then((_) => cancelled.complete());
        return Completer<ResponseBody>().future;
      });
      final service = CourseService(
        dio: dio,
        requestTimeout: const Duration(milliseconds: 80),
      );
      final fetch = service.fetchUndergraduateCourses(
        '2025',
        '1',
        courseColorPalette: AppCourseColorPalette.candyBox,
      );
      await started.future.timeout(const Duration(milliseconds: 500));
      final result = await fetch.timeout(const Duration(milliseconds: 500));
      expect(result.failures.single.reason, contains('超时'));
      await cancelled.future.timeout(const Duration(milliseconds: 500));
    },
  );

  test(
    'graduate never-completing request throws a timeout and cancels',
    () async {
      final cancelled = Completer<void>();
      final dio = Dio();
      dio.httpClientAdapter = _Adapter((_, cancelFuture) {
        cancelFuture?.then((_) => cancelled.complete());
        return Completer<ResponseBody>().future;
      });
      final service = CourseService(
        dio: dio,
        requestTimeout: const Duration(milliseconds: 80),
      );
      await expectLater(
        service
            .fetchGraduateCourses(
              '2025',
              '1',
              courseColorPalette: AppCourseColorPalette.candyBox,
            )
            .timeout(const Duration(milliseconds: 500)),
        throwsA(
          isA<CourseRequestTimeoutException>().having(
            (error) => error.message,
            'message',
            contains('研究生教务系统请求超时'),
          ),
        ),
      );
      await cancelled.future.timeout(const Duration(milliseconds: 500));
    },
  );

  test('dripping graduate response is bounded by the total deadline', () async {
    final cancelled = Completer<void>();
    var chunks = 0;
    final dio = Dio();
    dio.httpClientAdapter = _Adapter((options, cancelFuture) async {
      expect(options.connectTimeout, const Duration(milliseconds: 200));
      expect(options.sendTimeout, const Duration(milliseconds: 200));
      expect(options.receiveTimeout, const Duration(milliseconds: 200));
      cancelFuture?.then((_) => cancelled.complete());
      return ResponseBody(
        Stream.periodic(const Duration(milliseconds: 10), (_) {
          chunks++;
          return Uint8List.fromList([32]);
        }),
        200,
      );
    });
    final service = CourseService(
      dio: dio,
      connectTimeout: const Duration(milliseconds: 200),
      sendTimeout: const Duration(milliseconds: 200),
      receiveTimeout: const Duration(milliseconds: 200),
      requestTimeout: const Duration(milliseconds: 80),
    );
    await expectLater(
      service
          .fetchGraduateCourses(
            '2025',
            '1',
            courseColorPalette: AppCourseColorPalette.candyBox,
          )
          .timeout(const Duration(milliseconds: 500)),
      throwsA(isA<CourseRequestTimeoutException>()),
    );
    expect(chunks, greaterThan(1));
    await cancelled.future.timeout(const Duration(milliseconds: 500));
  });

  test('failed requests also dispose their deadlines', () async {
    var cancellations = 0;
    final dio = Dio();
    dio.httpClientAdapter = _Adapter((options, cancelFuture) {
      cancelFuture?.then((_) => cancellations++);
      throw DioException(
        requestOptions: options,
        type: DioExceptionType.connectionError,
        message: 'connection failed',
      );
    });
    final service = CourseService(
      dio: dio,
      requestTimeout: const Duration(milliseconds: 60),
    );
    final undergraduate = await service.fetchUndergraduateCourses(
      '2025',
      '1',
      courseColorPalette: AppCourseColorPalette.candyBox,
    );
    expect(undergraduate.failures, hasLength(1));
    await expectLater(
      service.fetchGraduateCourses(
        '2025',
        '1',
        courseColorPalette: AppCourseColorPalette.candyBox,
      ),
      throwsA(isA<DioException>()),
    );
    await Future<void>.delayed(const Duration(milliseconds: 100));
    expect(cancellations, 0);
  });

  test(
    'successful requests dispose their deadlines without cancellation',
    () async {
      var cancellations = 0;
      final dio = Dio();
      dio.httpClientAdapter = _Adapter((options, cancelFuture) async {
        cancelFuture?.then((_) => cancellations++);
        if (options.uri.host == 'i.sjtu.edu.cn') {
          return ResponseBody.fromString('{"kbList":[]}', 200);
        }
        return ResponseBody.fromString(
          '{"code":"0","datas":{"xsjxrwcx":{"rows":[]}}}',
          200,
        );
      });
      final service = CourseService(
        dio: dio,
        requestTimeout: const Duration(milliseconds: 60),
      );
      final undergraduate = await service.fetchUndergraduateCourses(
        '2025',
        '1',
        courseColorPalette: AppCourseColorPalette.candyBox,
      );
      final graduate = await service.fetchGraduateCourses(
        '2025',
        '1',
        courseColorPalette: AppCourseColorPalette.candyBox,
      );
      expect(undergraduate.failures, isEmpty);
      expect(graduate.courses, isEmpty);
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(cancellations, 0);
    },
  );

  test(
    'course credits are parsed from undergraduate and graduate payloads',
    () async {
      final dio = Dio();
      dio.httpClientAdapter = _Adapter((options, _) async {
        if (options.uri.host == 'i.sjtu.edu.cn') {
          return ResponseBody.fromString(
            '{"kbList":[{"kch":"PHY101","kcmc":"Physics","xm":"Li",'
            '"cdmc":"A101","jcs":"1-2","zcd":"1-16周","xqj":"1","xf":"3.5"}]}',
            200,
          );
        }
        return ResponseBody.fromString(
          '{"code":"0","datas":{"xsjxrwcx":{"rows":['
          '{"KCDM":"CHEM101","KCMC":"Chemistry","RKJS":"Wang",'
          '"PKDD":"B202","PKSJDD":"1-16周 星期一[3-4节] B202","XF":"2.0"}]}}}',
          200,
        );
      });
      final service = CourseService(dio: dio);

      final undergraduate = await service.fetchUndergraduateCourses(
        '2025',
        '1',
        courseColorPalette: AppCourseColorPalette.candyBox,
      );
      final graduate = await service.fetchGraduateCourses(
        '2025',
        '1',
        courseColorPalette: AppCourseColorPalette.candyBox,
      );

      expect(undergraduate.failures, isEmpty);
      expect(undergraduate.courses.single.credit, 3.5);
      expect(graduate.failures, isEmpty);
      expect(graduate.courses.single.credit, 2);
    },
  );
}
