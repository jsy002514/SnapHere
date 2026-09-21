import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:snap_here/src/bootstrap.dart';

Future<void> main() async {
  await dotenv.load(fileName: '.env');
  bootstrap();
}
