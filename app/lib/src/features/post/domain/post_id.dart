/// 기존 서버가 반환한 10진수 게시글 ID도 현재 상세 API의 외부 ID로 읽는다.
/// 이미 인코딩된 ID의 접미사는 36진수이므로 다시 숫자로 해석하지 않는다.
String postApiId(String value) {
  if (!RegExp(r'^[0-9]+$').hasMatch(value)) return value;
  return 'pst_${BigInt.parse(value).toRadixString(36)}';
}
