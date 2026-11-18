import 'package:course_block/core/models/course.dart';
import 'package:flutter_test/flutter_test.dart';

Course _course({double credit = 2.5}) => Course(
  courseId: 'CS101',
  courseName: 'Programming',
  teacher: 'Teacher',
  classRoom: 'A101',
  credit: credit,
  startWeek: 1,
  endWeek: 16,
  dayOfWeek: 1,
  startNode: 1,
  step: 2,
);

void main() {
  test('credit parser accepts numeric and labeled values', () {
    expect(Course.parseCredit(3), 3);
    expect(Course.parseCredit(1.5), 1.5);
    expect(Course.parseCredit('2.0 学分'), 2);
    expect(Course.parseCredit('未提供'), isNull);
    expect(Course.parseCredit(-1), isNull);
  });

  test('credit survives map and database-shaped round trips', () {
    final original = _course(credit: 2.5);
    final restored = Course.fromMap(original.toMap());

    expect(restored.credit, 2.5);
    expect(restored.syncFields['credit'], 2.5);
    expect(restored.copyWith().credit, 2.5);
    expect(restored.copyWith(credit: 3).credit, 3);
    expect(Course.fromMap({...original.toMap(), 'credit': null}).credit, 0);
  });
}
