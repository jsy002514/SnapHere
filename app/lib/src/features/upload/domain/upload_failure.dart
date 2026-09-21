enum UploadFailureReason {
  loginRequired,
  termsRequired,
  accountUnavailable,
  photoRead,
  photoCount,
  photoTooLarge,
  photoType,
  photoNotFound,
  photoUpload,
  photoUploadTimeout,
  photoUploadRejected,
  network,
  preparationTimeout,
  preparationFailed,
  serverUnavailable,
  placeRequired,
  placeNotFound,
  eventNotFound,
  invalidCoordinates,
  invalidTakenAt,
  tagInvalid,
  dailyLimit,
  placeDailyLimit,
  duplicateImage,
  uploadSuspended,
  mediaProcessing,
  mediaFailed,
  tooManyRequests,
  invalidInput,
  permissionDenied,
  conflict,
  resultUnknown,
}

/// 서버의 원문·저장소 URL 대신 사용자가 취할 수 있는 조치를 안내한다.
class UploadFailure implements Exception {
  const UploadFailure(this.reason);

  final UploadFailureReason reason;

  String get message => switch (reason) {
    UploadFailureReason.loginRequired =>
      '로그인이 만료됐거나 필요한 상태예요. 다시 로그인한 뒤 등록해 주세요.',
    UploadFailureReason.termsRequired =>
      '서비스 이용약관 동의가 필요해요. 약관에 동의한 뒤 등록해 주세요.',
    UploadFailureReason.accountUnavailable =>
      '현재 계정으로 게시글을 등록할 수 없어요. 계정 상태를 확인해 주세요.',
    UploadFailureReason.photoRead =>
      '선택한 사진 원본을 읽을 수 없어요. 사진 접근 권한을 확인하고 사진을 다시 선택해 주세요.',
    UploadFailureReason.photoCount => '사진은 1~4장까지 등록할 수 있어요. 선택한 사진을 확인해 주세요.',
    UploadFailureReason.photoTooLarge =>
      '사진 용량이 업로드 한도를 넘었어요. 용량을 줄이거나 다른 사진을 선택해 주세요.',
    UploadFailureReason.photoType =>
      '지원하지 않는 사진 형식이에요. JPG, PNG, HEIC 또는 WebP 사진을 선택해 주세요.',
    UploadFailureReason.photoNotFound =>
      '업로드한 사진을 확인할 수 없어요. 사진을 다시 선택한 뒤 등록해 주세요.',
    UploadFailureReason.photoUpload =>
      '사진을 전송하지 못했어요. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.',
    UploadFailureReason.photoUploadTimeout =>
      '사진 전송 시간이 초과됐어요. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.',
    UploadFailureReason.photoUploadRejected =>
      '사진 업로드가 거부됐어요. 다시 시도해 주세요. 계속되면 문의해 주세요.',
    UploadFailureReason.network =>
      '서버에 연결하지 못해 등록을 시작하지 못했어요. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.',
    UploadFailureReason.preparationTimeout =>
      '업로드 준비 시간이 초과됐어요. 잠시 후 다시 시도해 주세요.',
    UploadFailureReason.preparationFailed =>
      '업로드를 준비하지 못했어요. 사진과 장소를 확인한 뒤 다시 시도해 주세요.',
    UploadFailureReason.serverUnavailable =>
      '서버 문제로 업로드를 시작하지 못했어요. 잠시 후 다시 시도해 주세요.',
    UploadFailureReason.placeRequired => '게시글을 등록할 장소를 선택해 주세요.',
    UploadFailureReason.placeNotFound =>
      '선택한 장소를 찾을 수 없어요. 장소를 다시 검색해 선택해 주세요.',
    UploadFailureReason.eventNotFound =>
      '선택한 행사를 찾을 수 없어요. 행사 정보를 확인한 뒤 다시 작성해 주세요.',
    UploadFailureReason.invalidCoordinates =>
      '사진의 위치 정보를 확인할 수 없어요. 위치 정보와 선택한 장소를 확인해 주세요.',
    UploadFailureReason.invalidTakenAt =>
      '사진의 촬영 시간을 확인할 수 없어요. 기기 날짜·시간을 확인하거나 다른 사진을 선택해 주세요.',
    UploadFailureReason.tagInvalid =>
      '태그 정보를 확인하지 못했어요. 장소를 다시 선택하고, 자동 태그를 포함해 태그가 10개 이내인지 확인해 주세요.',
    UploadFailureReason.dailyLimit =>
      '오늘 등록할 수 있는 게시글 수를 모두 사용했어요. 내일 다시 등록해 주세요.',
    UploadFailureReason.placeDailyLimit =>
      '이 장소에 오늘 등록할 수 있는 게시글 수를 모두 사용했어요. 내일 다시 등록해 주세요.',
    UploadFailureReason.duplicateImage =>
      '이미 등록한 사진이에요. 내 게시글을 확인하거나 다른 사진을 선택해 주세요.',
    UploadFailureReason.uploadSuspended =>
      '현재 게시글 등록이 제한된 계정이에요. 이용 제한 안내를 확인해 주세요.',
    UploadFailureReason.mediaProcessing =>
      '사진을 처리하고 있어요. 내 게시글을 먼저 확인하고, 보이지 않으면 잠시 후 다시 확인해 주세요.',
    UploadFailureReason.mediaFailed => '사진 처리에 실패했어요. 사진을 다시 선택해 등록해 주세요.',
    UploadFailureReason.tooManyRequests => '요청이 너무 많아요. 잠시 기다린 뒤 다시 시도해 주세요.',
    UploadFailureReason.invalidInput =>
      '등록 정보가 올바르지 않아요. 사진·장소·태그와 작성 내용을 확인해 주세요.',
    UploadFailureReason.permissionDenied => '게시글을 등록할 권한이 없어요. 계정 상태를 확인해 주세요.',
    UploadFailureReason.conflict =>
      '현재 상태에서는 등록할 수 없어요. 내 게시글과 선택한 정보를 확인해 주세요.',
    UploadFailureReason.resultUnknown =>
      '등록 결과를 확인하지 못했어요. 이미 등록됐을 수 있으니 내 게시글을 먼저 확인해 주세요.',
  };

  @override
  String toString() => message;
}
