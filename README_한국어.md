# MY LINE v19 — 식단 안전 편집 / APK 웹 빌드

## 이번 변경
- 계획 식단을 수정할 때 저장/취소. 새 메뉴 검색이 기존 식사 전체를 즉시 덮어쓰지 않음.
- **전체 메뉴 변경**, **음식 추가**, **개별 바꾸기**, **재료 삭제** 분리.
- 각 재료 g 수정 후 **계획 저장**을 눌러야 반영. 칼로리/탄수화물/단백질/지방/당류 자동 계산 (기본 식재료 추정치).
- 실제 섭취 입력도 수정→저장/취소. 기존 입력을 덮어쓸 때 확인. 변경 후 과거 기록 보존.
- 온라인 검색: WebView의 CORS 제한에 대비한 Android 네이티브 HTTP 검색 통로. Open Food Facts 무료 API 기반. 외부 서비스 오류 또는 데이터 부족 시 앱 내 로컬 검색 사용.
- v18의 `myline_12weeks_20261116_v1` 저장 키와 JSON 백업 구조 유지. 앱 ID `com.myline.tracker` 유지.

## 설치 없이 GitHub Actions에서 APK 만들기
1. https://github.com/new 에서 저장소를 만들고 가능하면 Private(비공개)로 선택.
2. 이 ZIP 파일의 압축을 풀어 **그 안의 파일/폴더** (`app`, `.github`, `build.gradle`, `settings.gradle`, `gradle.properties` 등)를 GitHub 저장소의 루트에 업로드. ZIP 자체를 업로드하면 자동으로 압축 해제되지 않음. `.github` 폴더를 반드시 포함.
3. GitHub 저장소 `Actions` 탭 → `Build MY LINE APK (no local tools)` → `Run workflow` → `Run workflow`.
4. 빌드 완료 후 해당 실행 결과 하단 `Artifacts`의 `MY_LINE_v19_APK`를 내려받아 ZIP 압축을 풀면 `app-debug.apk`가 있음.
5. APK를 갤럭시로 옮겨 설치. 기존 앱 ID가 같고 서명도 같아야 업데이트가 가능. GitHub Actions에서 새 디버그 서명키를 사용하면 기존 디버그 앱에 바로 덮어쓰지 못할 수 있음. 따라서 **기존 앱 JSON 백업을 먼저 확보**하고, 필요시 구 버전을 삭제한 후 새 APK 설치/복원해야 함.

## 중요
- GitHub Private Actions는 무료 제공량 내에서 사용. 예산 초과로 결제되지 않게 GitHub Billing의 예산/사용량 제한 설정 확인.
- 인터넷 없는 상태에도 84일 계획, 체중·운동 기록과 로컬 재료 검색은 계속 사용 가능.
- Open Food Facts 검색 결과는 불완전할 수 있고 일부 네트워크에서 차단될 수 있음. 영양성분은 라벨과 검증.
- 이 환경에서는 Android SDK APK 실제 컴파일 및 갤럭시 설치를 수행하지 못했으며, 소스 코드 문법/브라우저 기능 테스트만 수행함.
