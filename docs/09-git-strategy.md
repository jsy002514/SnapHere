# Git 브랜치 전략
## 정할 내용
- git commit 시 설명 한국어 vs. 영어

## 한눈에 보는 흐름

```text
main
└── develop
    ├── feature/frontend-login
    ├── feature/backend-auth
    ├── feature/ai-inference
    ├── fix/backend-jwt-expire
    └── docs/git-strategy
```

1. `develop`에서 작업 브랜치를 생성합니다.
2. 작업 브랜치에서 기능 단위로 커밋합니다.
3. 작업이 끝나면 Merge Request를 생성합니다.
4. 리뷰 후 `develop`에 merge합니다.
5. 배포 가능한 상태가 되면 `develop`을 `main`에 merge합니다.

## 브랜치 역할

| 브랜치 | 역할 | 직접 커밋 |
| --- | --- | --- |
| `main` | 배포 가능한 안정 버전 | 금지 |
| `develop` | 개발 내용이 모이는 통합 브랜치 | 되도록 금지 |
| `feature/*` | 새로운 기능 개발 | 가능 |
| `fix/*` | 버그 수정 | 가능 |
| `docs/*` | 문서 수정 | 가능 |
| `chore/*` | 설정, 폴더 구조, 빌드 등 기타 작업 | 가능 |

## 브랜치 이름

```text
<type>/<scope>-<description>
```

| 구분 | 의미 | 예시 |
| --- | --- | --- |
| `type` | 작업 종류 | `feature`, `fix`, `docs`, `chore` |
| `scope` | 작업 영역 | `frontend`, `backend`, `ai`, `docs`, `common` |
| `description` | 작업 내용 | `login`, `auth`, `jwt-expire` |

### 좋은 예시

- `feature/frontend-login`
- `feature/backend-auth`
- `feature/ai-inference`
- `fix/backend-jwt-expire`
- `docs/git-strategy`
- `chore/init-structure`

### 피할 예시

- `login`
- `frontend`
- `test`
- `feature/login`
- `fix/error`

## 커밋 메시지

커밋 메시지는 Conventional Commits 형식을 사용합니다.
jira issue number을 입력하면 자동으로 jira에 링크됩니다.

```text
<type>(<scope>):<subject>
```

예시:

```text
feat(frontend): 로그인 페이지 구현
fix(backend): JWT 만료 예외 처리 수정
feat(ai): 이미지 분류 추론 API 연결
docs: Git 브랜치 전략 문서 추가
chore(common): 모노레포 초기 구조 설정
```

## Commit Type

| 타입 | 사용 시점 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `style` | 코드 포맷팅, 세미콜론, 공백 등 기능 변경 없는 수정 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `test` | 테스트 코드 추가 또는 수정 |
| `chore` | 빌드 설정, 패키지 설정, 폴더 구조 등 기타 작업 |
| `ci` | CI/CD 설정 변경 |
| `perf` | 성능 개선 |

## Commit Scope

| 스코프 | 의미 |
| --- | --- |
| `frontend` | 프론트엔드 |
| `backend` | 백엔드 |
| `docs` | 문서 |
| `common` | 공통 설정 |

`docs`, `chore`처럼 작업 영역이 명확하지 않거나 전체에 걸친 변경은 scope를 생략할 수 있습니다.


<br>

PR 메시지는 다음과 같은 형식을 사용합니다.

```text
[<type>](<scope>): <subject>
```
```test
[feat](frontend): 로그인 페이지 구현
```

## Merge Request 규칙

- MR은 작업 브랜치에서 `develop`으로 생성합니다.
- MR 제목은 커밋 메시지와 비슷하게 작성합니다.
- MR에는 작업 내용, 확인 방법, 참고 사항을 적습니다.
- merge 전에는 충돌을 해결하고 최신 `develop` 내용을 반영합니다.
- 최소 1명의 리뷰어를 거친 후 merge 합니다.

## Merge Request 템플릿

GitLab은 아래 파일을 기본 MR 설명 템플릿으로 사용합니다.

```text
.gitlab/merge_request_templates/Default.md
```

MR을 생성하면 템플릿 내용이 설명란에 자동으로 들어갑니다.

### 템플릿 항목 설명

| 항목 | 작성 내용 |
| --- | --- |
| 작업 영역 | 이번 MR이 영향을 주는 영역을 체크합니다. 여러 영역을 수정했다면 여러 개를 체크합니다. |
| 작업 내용 | 무엇을 왜 작업했는지 요약합니다. 너무 자세한 파일 목록보다 리뷰어가 이해해야 할 핵심을 적습니다. |
| 테스트 방법 | 리뷰어가 변경 내용을 어떻게 확인하면 되는지 적습니다. 실행 명령, 접속 경로, 테스트 방법 등을 포함합니다. |
| 체크리스트 | merge 전에 스스로 확인해야 하는 항목입니다. 해당하지 않는 항목은 체크하지 않아도 됩니다. |

### 작성 예시

```md
## 작업 영역

- [x] Frontend
- [ ] Backend
- [ ] AI
- [ ] Docs
- [ ] Common

## 작업 내용

- 로그인 페이지 UI를 구현했습니다.
- 이메일/비밀번호 입력값 검증을 추가했습니다.

## 테스트 방법

- `npm run dev` 실행
- `/login` 접속
- 빈 값으로 로그인 시 에러 메시지가 표시되는지 확인

## 체크리스트

- [x] 대상 브랜치가 `develop`입니다.
- [x] 브랜치 이름이 `[<type>](<scope>): <subject>` 형식입니다.
- [x] `.env`, API key, 비밀번호 등 민감 정보가 포함되지 않았습니다.
- [x] 빌드 결과물, 가상환경, 대용량 데이터/모델 파일이 포함되지 않았습니다.
- [x] 필요한 테스트 또는 직접 확인을 완료했습니다.
```

## 커밋 전 체크

- `.env` 같은 민감 정보가 포함되지 않았는지 확인합니다.
- 빌드 결과물, 가상환경, 대용량 데이터/모델 파일이 포함되지 않았는지 확인합니다.
- 한 커밋에 너무 많은 작업이 섞이지 않았는지 확인합니다.
- 커밋 메시지가 `feat(frontend): ...` 형식인지 확인합니다.
