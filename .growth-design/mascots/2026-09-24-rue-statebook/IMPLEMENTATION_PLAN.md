# 성인 Rue v6 — 상태별 레퍼런스 및 네이티브 적용 설계

> **후속 D-43 (2026-09-24):** 아래 D-42 설계 당시의 “앱 미적용” 문구는 역사 기록이다. 사용자의 전면 교체 지시로 **현재 로컬 iOS/Android 소스에 Rue 정지 8+4 리소스를 적용**, Kiwi 영상·번들 제거, 복구 실행 전 그림 수정 및 시뮬레이터/에뮬레이터 첫 화면 검증까지 진행했다. 원본 시트 자체는 덮어쓰지 않았으며 가공 해시는 [정적 에셋 manifest](../2026-09-24-rue-native/manifest.json). [의상 3종 비교](../2026-09-24-rue-wardrobe/index.html)와 [획득/판매 제안](../2026-09-24-rue-wardrobe/WARDROBE_PLAN.md)은 아직 앱 기능이 아니다. 현재 Clean alpha/권리·실기기 QA는 미완료, Play/실제 보드/출시 불변.

**결정 범위 (2026-09-24):** 사용자는 무릎 위 A라인 치마의 Rue v6를 **시각 방향으로 선택**하고 표정·상태별 레퍼런스 및 앱 적용 설계를 요청했다. 이 문서는 **설계**다. 이번 작업에서 iOS/Android 앱 코드·에셋, 공식 `off` 앱 아이콘, 실제 디자인보드 revision 20, Google Play 등록/이미지/출시/결제는 바꾸지 않는다. `MASCOTS.md`·`PROMPTS.md`·`manifest.json`은 생성 근거. 현재 세 마스터에는 halo/색 워시가 있고 제품용 clean alpha가 **검증되지 않았다**. 사용자 참고 그림 원본 재배포 없음.

## 1. 산출한 레퍼런스와 쓰임

| 범위 | 신작 그림 | 상태 | 용도 |
|---|---|---|---|
| 상반신 A, `assets/sheet-portraits-a.png` | 새 `image_gen` 마스터 1장 → 4칸 크롭 | `welcome`, `ready`, `focused`, `reflection` | 84×96pt 등 작은 UI에서 얼굴·손짓·상태 차이 연구 |
| 상반신 B, `assets/sheet-portraits-b.png` | 새 마스터 1장 → 4칸 크롭 | `finished`, `recovered`, `needs-action`, `failed` | 완료·복구·불명/주의·오류의 정직한 감정 연구 |
| 전신, `assets/sheet-fullbody.png` | 새 마스터 1장 → 4칸 크롭 | `welcome`, `ready`, `focused`, `recovered` | 성인 체형·짧은 치마·니트·신발을 유지하는 큰 화면용 방향 연구 |
| 선택 원형 | 새 생성 **아님**, v6 바이트 복사 | `assets/anchor-v6.png` | 스타일·의상 일치 판정 기준 |

**총 12개 새 상태 크롭**(표정 8 + 전신 4)과 선택된 기존 앵커 1개. 8개 표정은 **v4 재사용이 아니라 v6 앵커를 참조하여 새로 생성**한 레퍼런스다. 4개 전신은 동일한 무릎 위 밑단이 보인다. 표정 A의 무대 같은 짙은 배경, 표정 B의 붉은 가장자리, 전신의 halo가 남아 **네이티브 이미지로 직접 복사하지 않는다**. `prepare.py`의 크롭은 생성 그림을 분리할 뿐 alpha 정제/리터칭이 아니다. 각 상태의 정서·포즈가 정확한 앱 동작을 보장하지 않는다.

## 2. 현행 앱에 대한 소스 확인 (그래프는 과거 스냅샷)

- **iOS:** `RoomDNS/App/ContentView.swift:33–59`의 `RoomSpiritState`는 13상태. `:1512–1529`는 이를 키위 8종(`OffzoneKiwi-*`)에 매핑하고, `:1571–1710`의 `RoomSpirit`은 welcome/idle/returning/guiding/working에서 `kiwi-pingpong.mov`를 우선 재생한다. 따라서 이미지만 갈면 영상/저동작 포스터에 **키위가 남는다**. Welcome은 약 186pt(큰 글자150), Home 84×96pt, 설정 104×94pt, 준비 160pt, 복구 180pt(큰 글자120), Account 132pt 등 사용처가 다르다 (`ContentView.swift:262,357,846,1010,1308,1360`; `AccountViews.swift:20,279`). 인터랙션의 영어 접근성 문구도 `Your kiwi`/`Say hello to your kiwi`로 박혀 있다 (`ContentView.swift:1612,1659`). 현재 복구 sheet는 **실행 전에도** `.recovered`를 사용한다 (`:1308`); 시안 적용 시 사전 안내는 `ready`/`needs-action`으로 구분하고 성공 후에만 `recovered`를 표시해야 한다. `homeSpiritState` 우선순위는 error > place checking > needsAction > focused > recovered > transient reaction > idle (`:870–875`, `:33–58`), 이를 그대로 보존한다.
- **Android:** `android/app/src/main/java/com/exchip/offzone/KiwiView.kt:26–65`는 키위 8종 drawable과 greeting/shy에서 `kiwi_loop.webp` 애니메이션을 사용하고, 문자열 `kiwi_description`이 en/ko에 남아 있다. 하지만 화면 연결은 현재 `OnboardingScreen.kt:42`의 기본 GREETING과 `MainActivity.kt:131`의 세션 여부 기반 GREETING/SLEEPY **두 주 사용처**다. 8개 drawable 존재가 8개 상태의 앱 연결을 뜻하지 않는다. 아래 상세 상태 연결은 Android에서 **제안**이지 현재 구현이 아니다. 장소 확인·오류 메세지/복구는 `MainActivity.kt` 실제 UI/Controller 상태에서만 읽고, 그림만으로 성공을 주장하지 않는다.
- iOS/Android 모두 현재 앱의 주 행동은 사용자가 누르는 **Start focus**, 접근 복구는 **무료**. 과거 기획의 NFC/자동 도착 차단을 새 마스코트 UI에 되살리지 않는다. 공식 앱 아이콘은 `off` 워드마크이며 마스코트 변경과 분리한다. 화면 문안은 영어 원본/한국어 보조, 학업·교복·미성년·성적 캐릭터 포지셔닝 금지.

## 3. 13개 iOS 상태 → 새 레퍼런스 매핑 제안

그림은 **상태/권한/실제 제한 동작을 결정하지 않는다**. 기존 비즈니스 로직과 무료 복구 행동이 우선한다. 같은 이미지를 재사용하는 경우는 명시적으로 표기한다.

| `RoomSpiritState` | 상반신 ref | 전신 ref (공간·판독성 확보 시) | 보여줄 조건 / 피할 주장 |
|---|---|---|---|
| `.welcome` | `welcome` | `welcome` | 첫 진입; 시작·차단 전 |
| `.idle` | `ready` **공유** | `ready` **공유** | 규칙/권한과 무관하게 기본 중립; 완료 아님 |
| `.attentive` | `ready` **공유** | `ready` **공유** | 권한·대상 선택·검토 중; 아직 시작 전 |
| `.guiding` | `welcome` **공유** | `welcome` **공유** | 실제 설정 안내, 자동 실행 암시 금지 |
| `.working` | `reflection` **공유** | — | 위치/저장 확인 중; 이전 성공 표시 금지 |
| `.confirmed` | `ready` **공유** | `ready` **공유** | 규칙 저장/활성 준비 확인. 실제 집중 성공과 구별 |
| `.focused` | `focused` | `focused` | 모델의 확인된 세션에서만; 눈 감음은 수면/시간 절약 주장 아님 |
| `.needsAction` | `needs-action` | — | 권한/장소/저장 문제; 원인 텍스트+복구 행동 필수 |
| `.failed` | `failed` | — | 실제 오류; 캐릭터 단독 해결·책망 금지 |
| `.celebrating` | `ready` **공유** | — | `saveAndFinish()`는 규칙 저장이지 보호 시간 완료가 아님 |
| `.recovered` | `recovered` | `recovered` | 실제 무료 해제 **성공 후에만**. 사전 복구 안내는 `ready` |
| `.returning` | `welcome` **공유** | — | 48h 재방문 인사; 연속일수/효과 주장 아님 |
| `.finished` | `finished` | — | 실제 종료 상태 확인 후. 일정 전환·경합·오류면 우선 금지 |

**Android 단계적 매핑:** 지금 연결된 onboarding greeting→`welcome` portrait(전신은 170dp에서 얼굴 가독성 통과 시), home session null→`ready`, `session != null`→`focused` portrait. 추가로 `checkingPlace`→`reflection`, 실제 실패/권한 문제→`needs-action`/`failed`, 성공적으로 해제된 상태→`recovered`를 **Controller/UI가 실제로 제공하는 근거와 함께** 연결하는 별도 구현 작업. 단순 `session != null`이 OS 제한 성공을 증명한다고 주장하지 않는다. Android에 아직 없는 13개의 iOS 전이·모션을 존재한다고 표기하지 않는다.

## 4. 앱 적용 경로 (후속 코드 작업 설계, 이 턴 미실행)

1. **아트 QA 선행:** (a) 생성 원본·프롬프트·해시 보존, (b) 원본성/사용권·타인의 유사성 검토, (c) 생성된 붉은 가장자리·연기 배경을 전문 리터칭/재생성으로 정리한 *별도 버전*에서만 진짜 alpha/다크면 실측, (d) 버터 `#F7F0C7`·잉크 `#191B19`·민트/블루·사진 배경에 합성, 84×96pt 및 104×94pt에서 표정 구분, 큰 글자/저시력/명암 확인. 실패 시 이 팩을 앱으로 넣지 않는다. 원본 마스터를 덮어쓰지 않는다.
2. **명세·리소스 분리:** 새 `OffzoneRue-*` iOS imageset와 `rue_*` Android `drawable-nodpi`(완성된 투명 PNG 정적 프레임)를 별도 네임스페이스로 추가한다. 작은 프레임은 얼굴 위주 재구도한 상반신을, 충분한 세로 공간이 있을 때만 전신을 사용. 186/160pt iOS와 170dp Android도 전신 얼굴이 충분히 읽히는지 테스트한 뒤 채택. 형식/해상도·번들 크기·메모리 측정 후 결정. 원본 Kiwi 자산/루프는 백업·롤백용으로 유지; 공식 아이콘/스토어 이미지는 손대지 않는다.
3. **iOS 프레젠테이션 교체:** `RoomSpiritState` 비즈니스 계약과 우선순위 유지, `OffzoneKiwiExpression` 대신 독립 `RueExpression` 매핑과 `RoomSpirit`의 정적 이미지 렌더러를 시범 연결. `usesWalkingLoop`/`KiwiLoopView`/`kiwi-poster` 경로는 Rue 모드에 **절대 적용하지 않음**. 먼저 정지 컷만 배치하고 환영/홈/설정/준비/복구/계정/유료 안내 사용처를 모두 검사. 기존 기능/키위 자산을 삭제하지 말고, 내부 테스트용 설정/빌드 전환으로 롤백 가능하게 한다. `SafetyReleaseView`는 수행 전과 성공 후 표현을 구분. UI 상태 문구와 무료 접근 행동은 그림과 독립적으로 유지한다.
4. **Android 프레젠테이션 교체:** `KiwiView`의 `kiwi_loop.webp`가 Rue를 덮지 않게 독립 `RueView`(static first)와 `RueExpression`을 두고 `OnboardingScreen`/`MainActivity` 기존 두 사용처부터 교체. 그 뒤 실증된 `FocusController` 상태만 세분화; 권한/장소/복구는 원래 로직과 UI 문구 그대로. 복구 버튼·영어/한국어 접근성 설명을 사진 장식과 분리. Compose의 이미지 로드/가시성·리소스 최적화 검증.
5. **모션은 별도 검증 뒤:** 지금 12장은 **정지 PNG**. v4/v6 갤러리의 브라우저 CSS는 앱 모션이 아니고, iOS HEVC alpha MOV/Android WebP 키위 루프를 Rue라고 재라벨하지 않는다. 먼저 정적 상태만 출시 후보로 검토. 이후 짧은 비자동 1회 인사(성공 이후에만) 등 새 모션을 별도 제작·QA; Reduce Motion/Android 애니메이터 비활성·background/offscreen pause, 중복 음성·배터리, 정확한 종료 후 반응을 확인한다.
6. **테스트·게이트:** 상태 매핑 13개/Android 실제 연결 수를 단위 테스트, 리소스 누락·fallback(키위 또는 문자 상태)을 테스트. 로케일 en/ko, Dynamic Type/글자 확대, VoiceOver/TalkBack 읽기 순서(상태 텍스트가 이미 있으면 그림은 장식), interactive greeting이면 이름을 Rue로 재번역하되 Tap이 시작·해제·과금에 영향 없음. 로그인/유료 안내에도 무료 `Restore access`가 가려지지 않게 실제 기기에서 점검. iOS 시뮬레이터 + 서명된 iPhone (Screen Time/위치/복구) 및 실제 Android (Accessibility/장소/재진입) 모두 확인. 성공/실패/거부·늦은 콜백, Reduce Motion/애니메이터 OFF, 저사양·회전/백그라운드, dark/large text, 메모리/번들 크기 체크. 앱 교체·테스터 배포·스토어 자료 갱신/출시 판단은 각각 별도 단계다.

### 수용 기준 / 중단 기준

- **참고용 완성(이번 작업):** 새 3 마스터와 12 크롭·원본 해시·원본 v6 일치, 상태표/한계/웹 비교 확인. 앱 적용 완료가 아님.
- **네이티브 적용 착수 전:** clean alpha + 다중 배경, 저해상도 표정/텍스트 우선, 원본성·사용권, 누락 상태 표현, 롤백 경로 승인. 이 조건 중 하나라도 불명확하면 작업을 제안 단계에 둔다.
- **앱 QA 전환 후:** iOS/Android의 실제 상태 관찰·접근성·무료 복구·명시적 시작 테스트, 기존 Kiwi로 즉시 회귀 가능한 검증. 빌드 성공·웹 목업은 실기기·Play 출시 증거가 아니다.

**코드/자산 위치:** `RoomDNS/App/ContentView.swift`, `RoomDNS/App/AccountViews.swift`, `RoomDNS/Assets.xcassets/OffzoneKiwi-*.imageset`, `RoomDNS/KiwiMotion/`, `android/app/src/main/java/com/exchip/offzone/{KiwiView.kt,OnboardingScreen.kt,MainActivity.kt}`, `android/app/src/main/res/drawable*/kiwi*`, `android/app/src/main/res/values*/journal_strings.xml`. 전략 ID: ACT-01/SAFE-01/ALIGN-01/CORE-01. D-42는 **방향 선택+설계 허가**, 코드/스토어 출시 완료 표기가 아니다.
