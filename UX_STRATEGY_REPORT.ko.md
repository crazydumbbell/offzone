# Offzone(오프존) UI/UX 전략 보고서

## D-43 · Rue v6 현재 화면과 드레스룸 후속 제안 (2026-09-24)

**현재 코드:** iOS `RoomSpiritState` 13개→Rue 상반신 표정8/대형 전신4, Android onboarding/홈→Rue 정지 일러스트. Kiwi 영상·벡터/한영 접근성 명칭은 네이티브 번들에서 제거. 무료 Restore access 버튼·수동 Start focus 로직 불변. iOS 복구 **실행 전** `.attentive` 표시로 성공을 미리 주장하지 않음. 빌드/시뮬레이터·에뮬레이터 첫 화면과 두 플랫폼 관련 테스트는 확인했으나 양쪽 실기기 접근성/위치/Screen Time은 미확인. 새 의상3개는 전신 그림만 생성해 **장착 UI 미구현**; 헤어/얼굴·포즈를 상태 전체에서 고정하려면 별도 시트/rig 필요.

**후속 여정 제안:** Profile → 선택적 Wardrobe → 소유/미소유 필터 → 동일 상태에서 의상 미리보기 → 무료 기본 룩으로 되돌리기/소유한 의상 Equip → 원래 Focus 화면으로 복귀. 구매/무료 획득은 **미승인·미구현**임을 드레스룸 화면 전까지 고객 UI에 시사하지 않는다. 미래 보상·판매는 무료 기본 룩/무료 안전 복구·권한 안내/Start focus와 완전 분리, 집중 중 팝업/압박·streak/FOMO 금지; 무료 획득 원칙/결제·환불·미연결 계정·오프라인 상태의 명확한 상태 문구 필요. 장착이 차단 성공을 뜻하지 않으며 상태 텍스트/스크린리더 우선. 영어 원본·한국어 보조, 선택권과 접근성/실기기 QA가 출시에 선행. [의상 계약](.growth-design/mascots/2026-09-24-rue-wardrobe/WARDROBE_PLAN.md) · [기본 상태](.growth-design/mascots/2026-09-24-rue-statebook/index.html). 아래 예전 Roomie/키위 방향은 역사 기록이다.

**2026-09-07 브랜드 확정:** 영어 제품명은 **Offzone**, 한국어 제품명은 **오프존**이다. 마스코트 Roomie/룸이는 유지한다. 아래 코드 경로·기술 식별자·기존 NFC 주소는 표시명과 구분한다.

**2026-09-08 공식 로고 확정·색상 수정:** 제공된 `off` 워드마크 형태를 AppIcon으로 사용한다. 최신 지시에 따라 앱과 Roomie는 기존 크림·딥그린·코발트 체계를 유지하고, 로고만 종이색 `#FAF9F3`과 딥그린 `#223E38`로 맞춘다.

Strategy revision: **G1** · R1 구현 반영: **2026-09-07**

## 최신 장소 시작 흐름 — 2026-09-16

사용자 승인으로 NFC를 출시 범위에서 제거했다. 아래 과거 NFC 제안과 자동 시작 설명보다 이 절이 우선한다. `RULE-01`·`ACT-01`의 현재 UI는 Apple 지도 장소/주소 검색 → 결과 선택 → 지도 탭 또는 중심 조정 → 일정 → Save → Activate → 도착 후 Start focus다. 검색 요청 취소·오래된 결과 무시·결과 없음·실패 시 지도 선택 경로를 제공한다. 기존 태그 규칙은 장소 편집을 요구하며 실행하지 않는다.

`CORE-01`의 약속은 **약 150m 장소 범위와 사용자의 명시적 시작 확인**이다. 지도 공급자 교체로 GPS 정확도가 개선된다거나 방·책상 단위로 감지한다고 설명하지 않는다. Home의 Start focus는 활성 규칙·일정·권한·신선한 위치 조건을 반영하며, 불가 사유와 Check location을 표시한다. Save/Activate는 집중 시작과 구분한다. `SAFE-01` 무료 복구와 오류 표시는 유지한다.

구현 의존성은 ContentView의 MKLocalSearch/MapCamera와 AppModel의 canStartPlaceFocus/placeArrivalText/startPlaceFocus/checkPlaceArrival 및 공통 세션 정책이다. 장소 밖 시작 방지·경계 왕복·일정 종료·복구 후 무단 재시작 방지가 완료 조건이다. 검색 결과 선택과 지도 중심 버튼은 VoiceOver로 이름을 제공하지만 실제 기기 접근성·위치 정확도·백그라운드 종료 성공은 별도 검증한다. 측정할 결과는 장소 선택/시작/복구 과제 성공 및 오작동 건수이며 측정 완료로 주장하지 않는다. 기존 스타일·시스템 서체·영어 원본과 한국어 보조 현지화를 유지한다.


## 최신 네이티브 디자인 반영 — 2026-09-07

사용자의 실제 앱 적용 지시에 따라 [ContentView.swift](RoomDNS/App/ContentView.swift)에 선택된 Roomie v2 시안의 크림색·녹색 잉크·중앙 상태 영역·얇은 규칙 목록·작은 설정 마스코트·차분한 복구 sheet를 반영했다. 후속 지시 “폰트는 기본디자인꺼 써”에 따라 기존 시스템 서체의 serif 제목, rounded 본문·행동, monospaced 시간·브랜드를 유지한다. 상세 토큰은 [DESIGN.md](DESIGN.md), 사건별 반응은 [MOTION.md](MOTION.md)의 최신 절이 이전 시각 제안보다 우선한다.

`ALIGN-01`·`ACT-01`·`RULE-01`의 사용자 행동은 실제 7개 화면을 따른다. 웹 시안에서 생략했던 앱 선택·장소·일정 단계를 유지하고, Home은 AppModel의 현재 상태·필요한 시간·재사용할 규칙 행동을 보여준다. 저장과 활성화, 미적용 변경, 일시중지와 재개, NFC 스캔·종료, GPS·권한 오류, 삭제 확인을 구분한다. `SAFE-01`의 상단 복구 진입과 무대기 실행도 유지한다. `isFocused`는 정책 계산 상태이며 실제 OS 차단 성공을 읽어 검증한 값으로 승격하지 않는다.

Roomie는 성공한 저장·활성화, 일정 종료 조건, 오류·복구 상태에 반응한다. Home·Welcome에서 탭하면 규칙을 바꾸지 않는 인사를 제공한다. 저장 규칙이 있고 로컬 마지막 방문에서 48시간 이상 지났을 때 복귀 인사를 시도하며, 오류·집중·접근 복구 상태가 장식 반응보다 우선한다. 재방문 타임스탬프 외에 분석 백엔드, streak, 푸시, 성장 보상은 추가하지 않았다. `INSIGHT-01`·`BREAK-01`의 기록·계획 휴식 제안을 구현 완료로 바꾸지 않는다.

이 체크포인트는 소스를 확인한 구현 설명이다. 빌드·시뮬레이터 결과는 오케스트레이터의 [실행 기록](R1_IMPLEMENTATION_STATUS.md)에서 관리한다. 실기기 차단·복구, VoiceOver·큰 글자·Reduce Motion의 실제 과제 성공, 재방문율 개선은 검증됐다고 주장하지 않는다. 시각 선호는 방향 선택의 근거이며 유지율 근거와 분리한다.

내부 설명은 한국어, 고객 화면·스토어·지원 문안의 원본은 영어다. 대상은 **디지털 습관을 스스로 관리하려는 글로벌 성인**이며 학생·한국 시장으로 한정하지 않는다. 한국어는 보조 현지화로 유지한다. 이 문서는 **R1에 반영된 현재 구현과 이후 화면·여정 제안을 구분**한다. 영어 원본·상시 무대기 복구·저장/적용 분리·삭제·오류 표시가 코드에 반영되었다. 수동 체험·필수 앱 자동 예외·실기기 접근성 검증은 미완료이며 R2/R3는 아직 시작하지 않았다.

공통 결정과 우선순위는 [ORCHESTRATOR.md](ORCHESTRATOR.md), 판매 약속과 가격 가설은 [세일즈 전략](SALES_STRATEGY_REPORT.ko.md), 현재 구조와 구현 의존성은 [앱 구성 그래프](APP_GRAPH.md), 구현 상태는 [메타데이터](APP_METADATA.json)를 따른다. 이 보고서는 별도 출시 일정이나 경쟁하는 백로그를 만들지 않는다. 구현·검증 결과의 상세 기록은 [R1 구현 상태](R1_IMPLEMENTATION_STATUS.md)를 따른다.

**2026-09-07 사용자 지시:** 불필요한 부연설명을 제거한다. 정상 Home은 짧은 상태·필요한 시간·행동·규칙 목록으로 읽히게 한다. 성공 메시지와 같은 의미의 반복 설명은 상시 표시하지 않는다. 권한 목적·장소 범위·태그 준비·복구 재개·삭제 결과는 해당 결정을 하는 곳에서만 짧게 안내한다. 이 기준은 아래의 과거 설명형 화면 제안보다 우선한다.

**2026-09-07 시각 개편 지시:** 기존 Roomie와 기능 흐름은 유지하고 밝은 종이색, 1pt 잉크 그리드, 절제된 파스텔 셀, 역할별 시스템 serif/rounded/monospaced/default 서체로 앱 소유 화면을 개편한다. 레퍼런스의 달력·완료·통계·탭은 미구현 기능이므로 추가하지 않는다. 이 기준은 아래의 다크 고정 설명보다 우선한다.

## 1. 경험의 중심: 무엇을 막는가보다 무엇을 지키는가

공통 브랜드 문장은 **“Make room for what matters.”**다. 그 아래에서 선택한 앱을 언제, 어떤 조건으로 제한하는지 구체적으로 설명한다. 감성 문장만으로 GPS 자동화·NFC 준비·Screen Time 권한의 작동 방식을 대신하지 않는다.

| 내부 상황 ID | 고객용 선택지 | 고객이 원하는 결과 | 규칙 예시·제안 문안 | 관찰할 가치 |
|---|---|---|---|---|
| `work` | `Work` | 일의 흐름을 지키기 | `Work time` · `Keep distractions out of your work time.` | 계획한 업무를 시작하고 유지했는가 |
| `rest` | `Rest` | 쉬려고 열었던 앱에서 오래 머무르지 않기 | `Wind-down` · `Make space to switch off.` | 계획한 휴식을 가졌는가; 수면 개선은 별도 증거 없이 주장하지 않음 |
| `presence` | `Time with people` | 함께 있는 사람에게 주의를 돌리기 | `Dinner time` · `Be here for the people with you.` | 대화·함께 보내는 시간에 도움이 되었는가 |
| `personal` | `Personal time` | 읽기·취미·학습 등 자신이 선택한 시간을 갖기 | `Personal time` · `Give your own plans some room.` | 고객이 정한 일을 할 수 있었는가 |

이는 별도 앱 모드 네 개를 만들자는 제안이 아니다. 같은 규칙 기능의 예시와 유입 문안을 상황별로 시험한다. 직업·학교·성별·건강정보를 물어 개인을 분류할 필요가 없다. 학습은 `Personal time`의 선택 가능한 예시다. 자기관리라는 시장 범위를 운동·식단·금융·치료 기능 추가로 해석하지 않는다.

기능 사용 시간 자체를 성공으로 삼지 않는다. 고객이 규칙을 믿고 앱을 닫아 자신의 생활로 돌아갈 수 있는 경험을 목표로 한다. 홈 체류 시간이 줄어도 상태 이해·반복 사용·고객이 원한 활동이 개선되면 좋은 신호다.

## 2. 현재 화면과 실제 코드의 차이부터 해결한다

현재 소스와 R1 변경을 검토했다. 오케스트레이터는 2026-09-07 문안 정리 후 시뮬레이터 XCTest **11개·실패 0개**를 확인했다. 상세 검증 범위는 [R1 구현 상태](R1_IMPLEMENTATION_STATUS.md)를 따른다. 시뮬레이터 검증은 실제 iPhone의 위치 이동·NFC·차단·해제·VoiceOver 성공을 증명하지 않는다. 이번 보고서 갱신 담당은 빌드나 실기기 테스트를 재실행하지 않았다. 디자인 문서의 와이어프레임과 문안 예시는 구현 증거가 아니다.

| 근거 | R1 현재 코드·상태 | 남은 검증·후속 작업 |
|---|---|---|
| [ContentView.swift](RoomDNS/App/ContentView.swift), `RoomScreen` | `welcome → permission → targets → place → schedule → review → home`. 수동 체험·기록·설정·Pro 화면은 없음 | 실제 첫 작동 확인은 기존 GPS/NFC 설정을 이용해야 함 |
| 같은 파일, `init(model:)` | 저장 규칙이 없고 저장 오류도 없을 때 Welcome. 비활성 규칙이 있거나 저장 오류가 있으면 Home. 밝은 에디토리얼 외관 유지 | 미완성 설정의 앱 재시작 복원은 별도 검토 |
| 같은 파일, `welcomeScreen`, `permissionScreen`, `targetsScreen` | 영어 소개 한 문장·권한 목적·필수 앱 선택 주의 표시. Welcome의 네 상황 나열은 제거. 중복 권한 요청과 이전 요청의 뒤늦은 화면 이동 방지 | 네 상황은 제품·세일즈 맥락으로 유지. 수동 첫 체험과 선택형 상황 질문은 미구현 |
| 같은 파일, `placeScreen`, `scheduleScreen` | GPS 150m·정확도/권한·일정 조건은 짧게 표시. NFC 준비 상세는 `Tag setup` 펼치기 안에 배치. 매일 반복 및 다음 날 종료 안내 | 요일·지도 검색·태그별 규칙 연결 미구현 |
| 같은 파일, `reviewScreen`; [AppModel.swift](RoomDNS/App/AppModel.swift), `saveRule`, `activateRule` | `Save rule`은 저장만 수행. Home의 `Activate`/`Apply changes`로 명시적 적용 | 실제 활성 변경·권한 오류에서의 기기 상태 확인 |
| [ContentView.swift](RoomDNS/App/ContentView.swift), `homeScreen`, `ruleRow`; [AppModel.swift](RoomDNS/App/AppModel.swift), `currentStatusDetail`, `hasUnappliedChanges` | Home은 짧은 상태와 필요할 때 적용 스냅샷 기준 시간만 표시. 규칙 이름·저장 시간은 목록에 표시하고 `Unapplied changes`/`Paused`/`Active` 한 줄로 상태 구분. 정상 메시지·단일 활성 규칙 설명 제거 | 저장본 시간과 실제 적용 시간을 혼동하지 않아야 함. 정책 상태는 OS 실제 차단 확인이 아님 |
| [ContentView.swift](RoomDNS/App/ContentView.swift), 상단 `safeAreaInset`, `SafetyReleaseView` | 온보딩과 Home에서 `Restore access` 진입. 10초 대기 제거, 표준 버튼으로 즉시 복구 요청 | 모든 실제 기기 상태의 해제 성공과 VoiceOver 접근성 검증 |
| [AppModel.swift](RoomDNS/App/AppModel.swift), `safetyReleaseDescription`, `safetyRelease`; [RestrictionState.swift](RoomDNS/Shared/RestrictionState.swift), `restoreAccess` | 정상 구간 종료와 오류 시 규칙 일시중지를 구분. GPS는 해제 종료 시각, NFC는 새 스캔, 오류 일시중지는 명시적 재적용을 안내 | 복구 저장 실패 시 제한이 돌아올 수 있다는 오류를 표시하고 재시도. 실제 파일/OS 오류 검증 필요 |
| [AppModel.swift](RoomDNS/App/AppModel.swift), `refresh`; [RestrictionState.swift](RoomDNS/Shared/RestrictionState.swift), `RuntimeState` | 적용 스냅샷·복구 상태를 별도로 저장하고 정책을 계산. 이전 의도의 늦은 콜백을 구분 | `isFocused`는 OS의 차단 성공을 읽어온 값이 아님 |
| [AppModel.swift](RoomDNS/App/AppModel.swift), `acceptFocusTag`, NFC delegate | 태그 URL 패턴 확인과 명시적 스캔. 일정 조건 유지 | 특정 태그 ID 저장·규칙 연결·태그 쓰기는 미구현 |
| [ContentView.swift](RoomDNS/App/ContentView.swift), `actionFeedback`; [AppModel.swift](RoomDNS/App/AppModel.swift), `errorMessage`, `reportError` | 권한·장소/NFC·Home에는 실제 오류만 표시. 정상 저장·스캔·복구 메시지는 상시 표시하지 않음. 사용자 NFC 취소는 오류로 표시하지 않음 | 비동기 NFC 실패·권한/위치 오류·복구 저장 실패의 가시성과 VoiceOver 확인 |
| 같은 파일, 삭제 `confirmationDialog`; [AppModel.swift](RoomDNS/App/AppModel.swift), `deleteRule` | 접근성 이름을 가진 휴지통 버튼에서 규칙 이름과 삭제 결과 확인 후 삭제. 활성 삭제는 적용 상태·감시 정리, 다른 규칙 자동 활성화 없음 | 저장 실패·활성 삭제의 실제 기기 상태 검증 |
| [AppModel.swift](RoomDNS/App/AppModel.swift), `roomString`; [project.pbxproj](RoomDNS.xcodeproj/project.pbxproj) | 고객 문안 영어 키·`developmentRegion = en`, 영어 권한 기본값, 영어/한국어 리소스 유지 | 런타임 키/형식 검사와 실제 언어·접근성 검증을 구분 |
| [ShieldConfigurationExtension.swift](Extensions/ShieldConfiguration/ShieldConfigurationExtension.swift), [ShieldActionExtension.swift](Extensions/ShieldAction/ShieldActionExtension.swift) | 정적 영어 원본 shield·한국어 현지화, OS 버전별 앱 열기/닫기 응답 | 지원 OS별 실제 동작 확인; 필수 앱 자동 예외는 없음 |

가장 먼저 읽혀야 할 불일치는 **저장됨 ≠ 현재 활성화됨 ≠ 실제 차단 확인됨**이다. 이 세 가지를 문구·상태·기록에서 하나로 합치면 온보딩 성공률과 신뢰성 지표도 과장된다.

## 3. 영어 원본 콘텐츠 전략 — ALIGN-01

공통 문안을 영어로 먼저 작성한 뒤 실제 화면에서 읽고, 한국어로 현지화한다. 영어 사용자는 모국어·비모국어 모두 포함하므로 짧고 직접적인 동사를 쓴다. `threshold`, `shield`, `Focus Spot` 같은 브랜드·API 용어만으로 행동을 설명하지 않는다. 기술적으로 shield를 적용하더라도 고객에게는 `Block selected apps`처럼 의미를 설명할 수 있다.

| 맥락 | 제안하는 영어 원본 | 사용 조건 |
|---|---|---|
| 브랜드 첫 문장 | `Make room for what matters.` | 모든 상황의 상위 약속 |
| 앱 첫 소개 | `Choose when and where to block distractions.` | 현재 Welcome의 한 문장. 긴 기능 소개는 필요한 판매·지원 맥락에서 사용 |
| 선택 가능한 상황 질문 | `What would you like more room for?` | 건너뛸 수 있음. 응답은 예시만 조정 |
| 첫 설정 시작 | `Get started` | 현재 여정에도 사용 가능 |
| 수동 체험 | `Try blocking an app` | ACT-01 구현·복구 검증 이후에만 실제 버튼으로 사용 |
| 권한 설명 | `Offzone uses Screen Time to block the apps and websites you choose.` | 시스템 권한의 목적. 허가를 받은 것처럼 미리 표현하지 않음 |
| 시스템 요청 직전 버튼 | `Continue` | 설명 뒤 실제 시스템 요청을 열 때 |
| 대상 선택 | `Choose apps to put aside` | Apple picker 진입 |
| 필수 앱 안내 | `Keep essentials like calls, maps, and authentication available.` | 현재 자동 제외가 없음을 감추지 않으며 선택·검토에서 안내 |
| 홈 상태 | `Scheduled` / `In focus` / `Paused` | 현재 상태를 짧게 구분. 필요할 때 시간 한 줄을 추가 |
| 해제 진입 | `Restore access` | R1: 온보딩·Home 상시 진입과 무대기 복구 버튼 구현 |
| 오류 | `Your rule needs attention` / `Review setup` | 무엇이 문제인지 세부 이유와 행동을 함께 제공 |
| 회고 | `Did this help you do what you intended?` | 선택 응답; 실패 평가 없음 |

R1에서 [AppModel.swift](RoomDNS/App/AppModel.swift)의 `roomString()` 호출, ContentView, ShieldConfiguration의 고객 문안을 영어 키로 전환했다. [project.pbxproj](RoomDNS.xcodeproj/project.pbxproj)의 개발 언어는 `en`, 권한 기본 문안도 영어다. [영어 리소스](RoomDNS/en.lproj/Localizable.strings)와 [한국어 리소스](RoomDNS/ko.lproj/Localizable.strings)에 현재 키를 함께 제공하고 과거 한국어 키는 호환용으로 보존했다. 앱과 ShieldConfiguration은 같은 현지화 리소스를 각 번들에 포함한다. 고객이 저장한 규칙 이름을 번역하거나 덮어쓰지 않는다.

**완료된 코드 전환과 남은 출시 검증은 구분한다.** 현재 소스 키·번역 존재·형식 검사는 수행했지만 영어 우선 제품의 전체 기기 검증은 아직 완료되지 않았다.

1. `en-US`, `en-GB`, `ko`에서 12/24시간·날짜·복수형·단위·긴 문장을 확인한다. 현재 날짜/시간은 시스템 형식을 사용한다. GPS 150m는 설정 경계이며 위치 정확도의 보장이 아니다.
2. 앱별 언어 변경과 재실행 후 메인 앱·shield·NFC 안내·권한 설명이 의도한 언어로 읽히는지 실제 iPhone에서 확인한다.
3. VoiceOver·최대 접근성 글씨·Reduce Motion에서 설정·적용·수정·삭제·복구를 완료한다. 원화와 AM/PM을 문안에 고정하지 않는다.
4. 이후 새 화면과 모델 오류도 영어 원본과 한국어 번역을 같은 변경으로 갱신한다. 새 번역 서비스나 런타임 의존성은 추가하지 않는다.

## 4. 정보 구조: 홈을 중심으로 작게 확장한다

현재 IA는 7개 `RoomScreen`과 무료 해제 sheet다. 기록이나 설정 탭이 이미 존재하는 것처럼 그리지 않는다. 현재 구조의 전체 그래프는 [APP_GRAPH.md](APP_GRAPH.md)를 따른다.

**현재 Home 기반의 확장 IA**는 아래와 같다. R1 기능은 대괄호 없이, 미구현 제안은 대괄호 안에 표시한다. 첫 체험을 앞당기는 온보딩 순서는 아직 제안이다. 실제 사용 목적이 커지기 전에는 탭 바를 만들지 않는다.

```text
Welcome
  → [Optional context choice]
  → Screen Time explanation → System authorization → Target picker
  → [First working test → Restore and confirm access]
  → Home

Home — short status, relevant time, next action
  → Rules — save/edit separately from activate/apply changes, delete
      → [Duplicate, weekdays, tag binding]
  → [Plan a break → Resume / End session]
  → [Last 7 days → Reflection]
  → [Settings & help → Permissions, privacy, local data, purchase management]
  → Restore access — available during setup and on Home (R1)
  → [Pro — only from relevant feature exploration or Settings]
```

아래 상태 표는 후속 목표 문안까지 포함한다. 현재 R1은 짧은 `currentStatusTitle`과 필요할 때만 `currentStatusDetail` 시간 한 줄, 상단 복구 진입을 제공한다. 정상 설명 문단을 덧붙이지 않는다. Help·독립 일정 상세·다음 요일·계획 휴식 버튼은 아직 없다.

홈의 일반 텍스트 크기에서 시각적 순서는 **현재 상태 → 필요한 시간 → 지금 필요한 행동 → 규칙 목록**이다. 규칙 이름·시간은 목록에서 한 번 읽히게 하고 변경 미적용 상태만 짧게 구분한다. 룸이는 상태를 보조한다. 접근성 큰 글씨에서는 모든 콘텐츠가 첫 화면에 들어간다는 목표 대신 순서와 스크롤 접근성을 보장한다.

| 홈 상태 | 제안 표시 | 주 행동 | 지속 제공할 행동 |
|---|---|---|---|
| 저장 규칙 없음 | `Create your first rule` | `Create a rule` 또는 검증된 첫 체험 | `Help` / `Restore access` |
| 저장됐지만 미적용 | `Ready when you are` | 목록의 `Activate` | `Restore access` |
| 일정 밖 | `Scheduled` + `Starts at 9:00 AM` | 후속 제안 `View schedule` | `Restore access` |
| GPS 장소 밖 | `Outside your place` | 후속 제안 `View place` | `Restore access` |
| 정책상 실행 중 | `In focus` + `Until 5:00 PM` | 목록의 규칙 편집 | `Restore access`; BREAK-01 완료 후 `Take a break` |
| 권한·장소 불명확 | 짧은 상태 + 해결할 실제 오류 | `Open Settings` 등 해결 행동 | `Restore access` |
| 무료 해제 후 | `Access restored` / `Paused`, 필요한 경우 시각 | 현재 지원하는 스캔·규칙 재개; 자세한 재개 조건은 복구 sheet | `Restore access` |
| 계획 휴식 중 — 제안 | `On a break` + 정확한 복귀 예정 시각 | `Resume now` | `End session` / `Restore access` |

예시 요일·시각은 레이아웃 설명용이며 현재 요일 기능을 암시하지 않는다. 실제 복귀 시각을 확신할 수 없으면 `Scheduled until…`처럼 일정과 현재 확인 상태를 구분한다. 보이지 않는 위치 이벤트를 추측해 `You arrived` 또는 `Everything is blocked`라고 단정하지 않는다.

## 5. 첫 가치 경험과 권한 순서 — ACT-01 / CORE-01

문제는 새 사용자가 장소 권한 또는 준비된 NFC 태그 없이는 차단의 효용을 확인하기 어렵다는 점이다. 권고 여정은 **설명 → 필요한 Screen Time 권한 → 한 대상 선택 → 차단 확인·복구 경험 → 장소 자동화 구성**이다. 수동 체험은 현재 구현에 없으며 별도 상태와 안전한 종료 경로가 필요하다.

Apple은 온보딩에서 직접 해보며 배우고 비필수 설정·구매 요청을 미루는 방식을 권한다. 이 원칙을 위 여정에 적용하되, 첫 체험이 필수 권한을 우회한다고 설명하지 않는다. [Apple Onboarding](https://developer.apple.com/design/human-interface-guidelines/onboarding?changes=_1_1)

| 단계 | 화면 행동과 영어 문안 | 실패·이탈 경로 | 완료 증거 |
|---|---|---|---|
| 가치 이해 | 짧은 설명 + `Get started`; 상황 선택은 선택 사항 | 상황 질문 건너뛰기 | 고객이 무엇을 제한하고 무엇을 위해 쓰는지 자신의 말로 설명 |
| Screen Time | 목적 설명 후 `Continue` → 시스템 개인 권한 | 거부 시 기능 제한을 설명하고 재시도·도움. 앱 실행 때마다 요청하지 않음 | 실제 승인 상태 확인; 버튼 탭을 승인으로 계산하지 않음 |
| 대상 선택 | `Choose apps to put aside` → Apple picker | 취소하면 기존 선택 유지; 0개면 이유 표시 | 대상 1개 이상 선택; 카테고리를 앱 1개처럼 세지 않음 |
| 첫 체험 — 제안 | `Try blocking an app`; 선택 앱을 직접 열어 차단 화면 확인 | `It didn't work` → 복구·설정 검토; 체험 취소도 복구 | 적용 시도와 사용자 확인을 분리 기록 |
| 복구 확인 — 제안 | Offzone 복귀 → `Restore access` → 선택 앱 열기 확인 | 계속 막힘 → 지속 표시되는 복구·권한 안내 | 해제 요청과 실제 접근 확인을 별도 기록 |
| 반복 사용 설정 | `Make this part of your routine` → GPS/NFC/일정 | `Set up later`; 검증된 수동 기능이 없으면 현재 기능처럼 제공하지 않음 | 장소·태그·일정 검토, 저장과 활성화를 구분 |

`60-second check`는 빠른 사용자 확인 절차의 목표일 뿐이다. 60초 백그라운드 자동 해제 타이머의 보장이 아니다. 현재 코드의 일정 최소 길이는 15분이다. 짧은 테스트를 기존 매일 반복 일정으로 흉내 내거나 앱 foreground 타이머 하나로만 복구를 보장하지 않는다. 테스트 상태가 앱 종료 후 남는 문제를 먼저 검증한다.

위치 권한은 GPS 자동화를 선택했을 때 설명한다. 현재 위치 선택을 위한 권한과 앱이 열려 있지 않을 때의 장소 감지 권한의 이유를 분리하고, 거부 후에는 설정을 반복 강요하지 않는다. 시스템 요청 직전의 별도 사전 설명 화면은 `Continue` 등 중립적인 단일 진행 버튼을 쓰며, 시스템의 허용 선택을 모방하지 않는다. GPS/NFC 선택은 이 사전 설명 화면 이전에 둔다. [Apple Privacy: Requesting permission](https://developer.apple.com/design/human-interface-guidelines/privacy?changes=_10_7)

대상 선택은 `FamilyActivityPicker`를 유지한다. 현재 선택 개수는 앱·카테고리·도메인의 합이므로 `3 apps` 대신 `3 selected items` 같은 표현을 사용한다. 미리보기 이름·아이콘이 필요하면 Apple의 token `Label`을 사용하고 선택 앱명을 추출할 수 있다고 가정하지 않는다. [Apple FamilyActivityPicker](https://developer.apple.com/documentation/familycontrols/familyactivitypicker?changes=_3_3), [Displaying Activity Labels](https://developer.apple.com/documentation/familycontrols/displayingactivitylabels?changes=_5)

## 6. 규칙 생성·관리와 GPS/NFC 교육 — RULE-01

규칙은 **이름 + 대상 + 실행 조건 + 시간 + 종료 방식**으로 이해하게 한다. `Work time`, `Wind-down`, `Dinner time`, `Personal time`은 편집 가능한 시작 예시다. 무조건 직장·학교 위치를 저장하게 하지 않는다.

생성 화면은 현재 선택·지도·`DatePicker`를 재사용한다. 전체 입력을 더 긴 마법사로 늘리는 대신 규칙 검토에서 각 항목으로 돌아가 수정할 수 있게 한다. 저장 실패는 입력을 보존하고 해당 단계에 이유를 표시한다.

| 작업 | 현재 | 제안하는 상호작용·조건 |
|---|---|---|
| 저장과 사용 | R1: `Save rule`은 저장만, `Activate`/`Apply changes`는 명시적 적용 | 저장한 수정본과 적용 스냅샷을 계속 구분하고 실패 시 기존 세션 보존 검증 |
| 활성 전환 | R1: 활성 하나; 목록에서 명시적 적용, 새로 적용할 내용은 저장본 기준 | 적용 전 결과의 이해도와 실패 복구 확인. 복수 동시 실행은 미채택 |
| 삭제 | R1: 규칙 이름과 결과를 확인하는 삭제 대화상자 구현 | 활성 삭제의 감시/제한 정리와 저장 실패를 실기기에서 확인. 다른 규칙을 자동으로 켜지 않음 |
| 복제 | 없음 | `Duplicate rule`은 비활성 복사본 생성; 이름과 장소·태그를 고객이 검토 |
| 요일 | 매일 반복 | `Repeat` + 현지화 요일. `Every day` / `Weekdays`도 선택 예시이지 모두의 근무일 가정이 아님 |
| 자정·시간대 | 자정 경계 계산 있음; 실기기 검증 필요 | `10:00 PM–7:00 AM, next day`처럼 날짜 넘어감 설명. 요일은 시작 날짜 기준 등 규칙 의미를 확정하고 여행·DST 테스트 |
| 수정 취소 | draft를 재설정하고 홈 복귀 | 시스템 뒤로 가기와 취소를 일관되게 적용. 수정 중 실제 활성 규칙이 바뀌지 않는지 확인 |

GPS/NFC 선택은 우열 배지보다 **자동 장소 감지와 의도적 스캔의 차이**로 설명한다. R1에서는 GPS의 고정 `Recommended` 배지를 제거했다. 어느 방식이 맞는지 준비물·권한·시간 조건으로 판단하게 한다.

| 항목 | GPS place | NFC tag |
|---|---|---|
| 제안 제목 | `Start around a place` | `Start with a tag` |
| 짧은 설명 | `Use a 150 m boundary around a place. Location timing can vary.` | `Open Offzone and scan your prepared tag.` |
| 적합한 상황 | 집·사무실 등 장소 범위에서 자동 동작을 원하는 때 | 책상·현관·취미 공간 등 의도적으로 경계를 시작할 때 |
| 준비·권한 | 현 구현은 Always·Precise Location 필요 | 별도 NFC 태그, 올바른 NDEF URL, 지원 기기; 앱에서 스캔 시작 |
| 현재 한계 | 개별 방 감지·즉시 진입 판정 보장 없음 | 태그별 규칙 연결·앱 내 태그 쓰기 없음. 스캔 결과와 일정 조건이 함께 작동 |
| 실패 시 | 위치·권한 상태와 수정 행동, 무료 복구 | `This tag isn't ready for Offzone.` + 준비 안내·재시도·다른 방법 |

NFC 흐름은 `I have a tag`와 `How to prepare a tag`를 통해 구매 전에 준비 조건을 알게 해야 한다. 현재 예상 URL은 `https://roomdns.app/focus/<tag-id>`이며 도메인 소유·보편 링크·백그라운드 실행까지 검증된 것은 아니다. 태그 쓰기가 구현되기 전에는 `Create your tag` 버튼으로 이미 가능한 일처럼 표시하지 않는다.

태그 바인딩을 구현할 때는 **태그 읽기 → 표시 이름 정하기 → 연결 규칙 확인 → 다른 태그로 잘못 실행되지 않는지 테스트**를 거친다. 인식 성공만으로 차단 시작을 축하하지 않는다. 일정 밖에서 읽었으면 `Tag recognized. Your rule is scheduled for 6:00 PM.`처럼 구분한다. 스캔 취소는 오류로 꾸짖지 않는다. `readingAvailable`로 지원 여부를 확인하고, 미지원 기기에는 가능한 다른 경로를 제공한다. [Apple NFC readingAvailable](https://developer.apple.com/documentation/corenfc/nfcreadersession-swift.class/readingavailable)

## 7. 실행·계획 휴식·복구 — CORE-01 / SAFE-01 / BREAK-01

실행 화면은 짧은 상태, 필요한 종료 시각, 복구 행동으로 읽히게 한다. 상세 조건은 설정·복구 결정을 하는 곳에서 설명하고 Home에 반복하지 않는다. 룸이의 자세가 바뀌었거나 정책 함수가 참이 되었다는 이유로 사용 가능한 모든 앱이 실제 차단됐다고 단정하지 않는다.

R1에서 상시 복구 진입과 무대기 버튼을 구현했다. 과거 `isFocused` 조건부 진입·10초 대기는 제거되었다. 다음은 현재 동작과 남은 검증 계약이다.

- R1의 `Restore access`는 온보딩·Home 상단에서 추정 집중 상태와 무관하게 제공된다. 결제·로그인·네트워크·사유 입력을 요구하지 않는다. 설정·도움의 추가 진입점은 해당 화면 구현 이후다.
- R1은 표준 `Restore access now` 버튼을 즉시 사용할 수 있다. 시간 대기 없이 모델에 복구를 요청하고 Home의 상태·필요한 시각으로 결과를 표시한다. 실패 메시지는 별도로 유지하며 정상 성공 설명은 반복하지 않는다. 선택적 숙고·계획 휴식은 별도 미구현 경험이다.
- 정상 GPS 구간의 복구는 현재 일정 종료까지 해제하고 이후 일정 재개 가능성을 안내한다. NFC는 새로 시작한 명시적 스캔으로 재개한다. 권한/위치/저장 오류나 일정 밖 복구는 규칙을 일시중지하고 저장 규칙을 명시적으로 다시 적용하게 한다. 복구 저장 실패는 제한이 돌아올 수 있음을 알려 재시도를 요청한다.
- R1은 적용 상태와 의도 세대값을 저장해 이전 스캔/위치 콜백을 구분하고, 편집 저장으로 적용 스냅샷을 바꾸지 않는다. 시뮬레이터 검증과 별도로 실제 재시작·일정·권한 변경·늦은 이벤트에서 복구 유지 여부를 확인해야 한다.
- `Access restored`는 실제 검증 범위에 맞춰 사용한다. 불확실하면 `Restrictions cleared. Check that your apps open.`처럼 조치와 확인을 나눈다. 미해결 상태에서 성공 캐릭터를 띄우지 않는다.
- 필수 앱을 기본 제외한다고 판매하려면 카테고리 선택에 포함된 예외까지 처리할 방법을 확인해야 한다. 현재 안내문만으로 모든 전화·지도·인증·금융 앱이 자동으로 안전하다고 주장하지 않는다.

계획 휴식은 벌점 없는 일상적 행동이다. BREAK-01이 완료되면 `Take a break`에서 기간과 복귀 예정 시각을 확인하고, 휴식 중에는 `Resume now`와 `End session`을 제공한다. 임의의 짧은 기간을 약속하지 않으며 기술적으로 보장 가능한 범위를 확인한 뒤 시간 선택지를 정한다. 무료 계획 휴식을 유료 안전 해제와 섞지 않는다.

휴식 종료 전후의 앱 종료·기기 잠금·오프라인·권한 철회·시간대 변경을 테스트한다. 실제 복귀가 확인되지 않으면 `Check your rule`로 안내하고 누락된 구간을 보호 시간에 더하지 않는다. 수동 복귀만 가능한 상태라면 `Automatically resumes`라는 문안으로 판매하지 않는다.

Shield는 Apple이 관리하는 별도 표면이다. 현재 static 구성과 OS별 응답을 유지하는 범위에서 중립적 설명만 제공하고 구매 유도는 넣지 않는다. 최신 코드에서는 iOS 26.5 이상에 앱 열기 응답을 사용하고 그 이전에는 닫기이므로, 지원 OS별 행동·번역·접근성·실기기 결과가 맞아야 한다. 메인 앱을 직접 찾아야 하는 고객에게 `Open Offzone to review your rule.` 같은 대안을 명확히 안내한다.

## 8. 기록은 정직한 반복 가치로 만든다 — INSIGHT-01

현재 세션 기록·최근 7일 집계·분석 서버는 없다. 제안하는 첫 화면은 **지난 7일의 확인 가능한 제한 구간과 짧은 회고**이며, 홈에는 요약 한 줄만 둔다. 이 앱을 열어본 횟수를 성취로, 차단하도록 설정된 시간을 실제로 절약한 시간으로 바꾸지 않는다.

| 요소 | 제안 | 피해야 할 해석 |
|---|---|---|
| 첫 기록 전 | `Your sessions will appear here.` | 임의의 0분 실패 평가 |
| 제한 구간 | `Recorded protection time` + 계산 방법 | `You saved 4 hours`처럼 반사실적 절약 시간 단정 |
| 구간 불확실 | `Some time couldn't be confirmed.` | 앱 미실행·권한 공백을 전부 보호 시간으로 채우기 |
| 고객 회고 | `Did this help you do what you intended?` → `Yes` / `Partly` / `No` / `Skip` | 생산성·수면·정신 건강 점수로 확대 |
| 종료 유형 | 완료·계획 휴식·고객 해제·시스템 오류를 구분 | 무료 복구 사용을 실패로 낙인 |
| 데이터 제어 | 기록 삭제, 무엇이 저장되는지 설명 | 유료 결제 뒤에 삭제 기능 배치 |

기술적으로는 적용·해제·일정·권한·휴식 이벤트의 시각과 출처를 기록하고, 중복 콜백과 교차 자정 구간을 정리해야 한다. 메인 앱의 화면 타이머만으로 백그라운드 보호 시간을 채우지 않는다. OS 상태를 독립적으로 확인하지 못하는 경우에는 기록의 의미를 더 좁게 표시하거나 불확실 구간을 제외한다.

규칙 이름·좌표·선택 토큰·앱/도메인 이름·회고 자유 입력은 외부 분석에 보내지 않는다. 베타에서는 동의한 로컬 요약·인터뷰로 시작한다. 미래 원격 측정은 최소 이벤트와 개인정보 설명을 별도 검토하고, 기록 기능 추가 자체를 외부 업로드 동의로 해석하지 않는다.

## 9. Pro는 더 편한 반복 관리에서 제안한다 — OFFER-01

G1 사업 가설은 **직접 만든 자동화·규칙 관리·기록 경험의 확장**이다. 콘텐츠 팩 사업으로 바꾸지 않는다. 현재 결제 SDK·상품·entitlement·paywall은 구현되어 있지 않으며, 과거 15분 유료 통행권 명세는 현행 판매 기능이 아니다.

세일즈의 연간 US$19.99 / US$29.99는 비교할 가격 가설이며 확정·전체 국가 공통 가격이 아니다. 고객 화면에는 StoreKit에서 받은 실제 지역화 금액과 청구 주기를 표시한다. Apple 지침 4.10은 Screen Time API 같은 내장 기능 자체의 수익화를 제한하므로, 추가 경험을 Pro로 부른다는 이유만으로 심사 적합성이 확보되지는 않는다. 구현 가치·무료 기능·심사 설명을 오케스트레이터가 함께 검토한다. [Apple App Review Guidelines §4.10](https://developer.apple.com/app-store/review/guidelines/#monetizing-built-in-capabilities)

| 진입·상태 | 제안 경험 |
|---|---|
| 첫 설정·권한·첫 체험 | 구매 화면 없음 |
| 검증된 고급 자동화를 고객이 탐색 | 어떤 작업이 쉬워지는지 먼저 설명한 뒤 `Explore Pro` |
| 기존 저장 규칙 | 현재 여러 프리셋을 저장하는 기능을 갑자기 잠가 구매를 강요하지 않음. 기존 규칙 편집·삭제·안전 복구 유지 |
| 가격 화면 | `Offzone Pro` + 실제 제공 기능 + 전체 연간 청구액 + 자동 갱신·해지 설명 |
| 비구매 행동 | `Not now` 또는 명확한 닫기. draft와 기존 작업 유지 |
| 가격 로딩 실패 | `Plans are unavailable right now.` + 나중에 재시도. 임의 가격·가짜 할인 없음 |
| 구매 취소 | 원래 기능으로 조용히 복귀; 반복 할인·룸이의 실망 표현 없음 |
| 구매 대기/실패 | 상태를 구분하고 재구매를 유도하는 중복 버튼 방지. 제한 상태는 결제 결과로 임의 변경하지 않음 |
| 복원·구독 관리 | `Restore purchases`, `Manage subscription`, 약관·개인정보 링크 |
| 만료 | 데이터 삭제·고객의 접근 잠금 없이 범위 설명. 제한을 안전하게 해제·관리할 수 있는 경로 유지 |

예시 paywall 문안은 `Build routines that fit your life.`로 시작할 수 있다. 그 아래에는 실제 구현된 고급 기능만 넣는다. `Advanced schedules`, `Connected places and tags`, `Longer-term insights`는 아직 제안이며, 구현·검증 전 구매 가능한 것처럼 표시하지 않는다. 무료/Pro 기능 개수와 기록 기간은 오케스트레이터의 상품 결정 후에 채운다.

전체 청구액을 월 환산액보다 크게 보여주고, 상품 이름·기간·포함 내용·복원 경로를 명확히 제공한다. 체험 상품을 채택한다면 실제 자격·체험 기간·후속 청구액을 사용한다. 별도 결제 카드 UI나 계정 가입을 먼저 만들지 않는다. [Apple Auto-renewable Subscriptions](https://developer.apple.com/app-store/subscriptions/)

## 10. 성숙한 시각 언어와 접근성

[DESIGN.md](DESIGN.md)의 Quiet Threshold 토큰과 [MOTION.md](MOTION.md)의 네이티브 룸이를 재사용한다. 넓은 고객층을 공략한다고 디자인을 기업용 관리 대시보드로 바꾸거나, 새로운 UI 패키지를 추가할 필요는 없다.

앱 아이콘은 메타데이터에 기록된 **캐릭터 없는 후보**를 유지한다. 현재 선두 후보 `location_pin_with_pause`는 아직 선택·프로젝트 적용되지 않았다. **룸이는 앱 안에서 상태와 다음 행동을 돕는 역할**이다. 아이콘에 룸이가 없다는 결정이 앱 내 캐릭터를 없앤다는 뜻은 아니다. 성숙함·의미 이해는 실제 아이콘 크기에서 별도로 확인한다.

- 상태·원인·시각이 캐릭터보다 먼저 의미를 완성한다. 집중 중 룸이는 조용히 멈추고, 오류에는 해결 행동을 보조하며 구매 여부에 감정적으로 반응하지 않는다.
- 현재 앱 소유 화면은 밝은 에디토리얼 외관으로 고정한다. 별도 테마 시스템이나 외부 폰트를 만들지 않고, 실제 대비·텍스트·시스템 sheet를 확인한다.
- `DatePicker`, `Picker`, `Toggle`, `Form`/`List`, 시스템 sheet·확인 대화상자·`FamilyActivityPicker`를 먼저 쓴다. 기존 20pt 여백·50pt 주요 버튼·시스템 글꼴을 재사용한다.
- 모든 조작은 최소 44×44pt 터치 영역을 확보한다. 아이콘·색만으로 상태를 전달하지 않고 시스템 텍스트 스타일·Dynamic Type을 적용한다. 큰 글씨에서 작은 캡슐 배지·활성 버튼이 줄 잘림을 만들면 세로 배치로 바꾼다. [Apple UI Design Dos and Don’ts](https://developer.apple.com/design/tips/), [Apple Typography](https://developer.apple.com/design/human-interface-guidelines/typography?changes=_5)
- VoiceOver는 상시 복구 진입과 상태 → 필요한 시각 → 주요 행동 → 규칙 목록에 접근할 수 있어야 한다. 삭제 아이콘에는 규칙 이름을 포함한 접근성 이름을 유지하고, 숨긴 정상 설명을 음성에서 장황하게 반복하지 않는다. 룸이는 장식으로 숨기고 텍스트로 의미를 제공한다. 타이머가 매초 음성을 가로채지 않게 하며, 오류가 나면 해당 설명과 해결 행동에 접근할 수 있어야 한다.
- 지도 탭만으로 장소를 고르게 하지 않는다. 현재 위치 버튼이 있으나 위치 권한 거부·지도 탐색이 어려운 고객까지 고려해 검색 결과/주소 기반 선택을 RULE-01 보강으로 검토한다. NFC 스캔 안내도 시각적 파동만으로 전달하지 않는다.
- Reduce Motion에서 반복 호흡·점프·크기 변화는 끄고 정적 자세 또는 짧은 페이드로 상태를 전달한다. 모션 설정이 차단·복구 시점을 바꾸면 안 된다. [Apple Reduced Motion evaluation criteria](https://developer.apple.com/help/app-store-connect/manage-app-accessibility/reduced-motion-evaluation-criteria)

접근성 완료 표시는 코드의 `.accessibilityLabel` 존재 여부만으로 결정하지 않는다. 영어/한국어, VoiceOver, 가장 큰 접근성 글씨, Reduce Motion에서 첫 설정→규칙 편집→실행→복구를 끝까지 수행한 증거가 필요하다. 위험한 contrast·줄 잘림·초점 손실은 전환 실험보다 먼저 수정한다.

## 11. 세일즈 사례를 UX 실험으로 번역한다

실제 앱의 공개 기능·성공 근거·가격 출처는 [세일즈 전략의 사례 분석](SALES_STRATEGY_REPORT.ko.md)에 둔다. 아래는 그 사례에서 가져올 **Offzone의 설계 가설**이지 타사의 화면을 복제하거나 동일 효과를 보장하는 주장이 아니다.

| 사례 연결 | Offzone에서 시험할 행동 | 맡길 공통 ID | 채택하지 않을 것 |
|---|---|---|---|
| one sec: 의도 없는 열기 전에 멈춤 | 선택적 짧은 숙고 뒤 `Continue` / `End session`을 이해하는지 확인 | SAFE-01, BREAK-01 | 긴급 복구 지연, 타사 연구 수치를 우리 성과로 표시 |
| Opal·Jomo: 반복 규칙과 관리 경험 | 사용자 일상에 맞는 이름·조건·반복을 쉽게 수정 | RULE-01, OFFER-01 | 복잡한 설정을 첫 실행에 전부 노출, 강제 설정을 모든 고객에게 기본 적용 |
| Freedom: 반복 사용 편의의 유료 가치 | 무료 작동 경험 뒤 고급 자동화 가치를 보여주기 | ACT-01, OFFER-01 | 현재 없는 크로스 플랫폼·동기화를 판매 |
| Brick: 물리적 행동의 분명함 | NFC 준비·스캔·시작 상태를 고객이 구분하는지 확인 | RULE-01 | 태그가 없으면 절대 해제할 수 없다는 약속 |
| ScreenZen: 간결한 규칙 이해 | 선택·조건·시간을 한 검토 화면에서 이해 | ACT-01, RULE-01 | 상대 앱의 가격/정책이 우리 사업에도 맞는다고 가정 |

## 12. 판매 약속 → 화면 행동 → 의존성 → 측정

확신 수준에서 **높음**은 정적 코드 근거, **가설**은 사용성/사업 검증 전 제안이다. 어느 것도 실기기 합격을 뜻하지 않는다. 기능의 실행 순서는 [ORCHESTRATOR.md](ORCHESTRATOR.md)의 공통 백로그를 따른다.

| ID | 고객 문제·판매 약속 | UX 행동 | 코드·구조 의존성 | 측정·완료 조건 | 확신·열린 결정 |
|---|---|---|---|---|---|
| ALIGN-01 | 영어로 이해 가능한 자기관리 | 글로벌 상황 문안·영어 설정·오류·지원 | `roomString`, `.lproj`, Xcode 개발 언어, extension 자산 | 핵심 여정에 의도치 않은 한국어 0건; 고객이 기능/조건을 설명 | R1 영어 원본/기본 언어 전환·번역 검사 완료; 기기 언어/AX 검증 남음 |
| CORE-01 | 선택한 규칙을 믿고 일상으로 돌아가기 | 상태/원인/종료 조건·작동 확인 | `refresh`, `RestrictionPolicy`, `reconcileRestrictions`, DeviceActivity·Location | 대상 접근 확인; 시간/권한/재시작별 지속 차단 결함 0건 | 구조 높음·실기기 미검증 |
| SAFE-01 | 통제권은 고객에게 남음 | 모든 상태의 `Restore access`, 예외 선택·후속 확인 | 조건 없는 복구 진입, 공통 상태·예외 처리 | 관찰 고객 모두 복구 발견/완료; 무료·오프라인·권한 오류 경로 검증 | R1 상시 무대기 복구 구현·시뮬레이터 검증; 필수 앱 예외·실기기 남음 |
| ACT-01 | 많은 준비 전에 작동을 이해 | 첫 대상 → 체험 → 복구 → 반복 설정 | 수동 테스트 상태·종료/복구·단계별 오류 표시 | 첫 차단/복구 확인까지 시간, 무도움 완료율, 거부 후 복귀 | 사용성 가설; manual mode 없음 |
| RULE-01 | 일·휴식·함께하는 시간에 맞추기 | 저장/사용 분리, 삭제/복제/요일, GPS/NFC 안내 | `FocusRule`, `saveRule`, 일정 저장, 태그 ID 파싱·연결 | 잘못 활성화 0건; 수정/삭제 후 shield·일정 일치; 올바른 태그만 작동 | R1 저장/적용 분리·삭제 구현; 복제/요일/태그 연결 미구현 |
| INSIGHT-01 | 반복 사용의 변화를 이해 | 정직한 구간·불확실 상태·선택 회고 | 로컬 이벤트 기록·중복 제거·구간 계산 | 공백/휴식/해제 과대 집계 0건; 계획한 활동 도움 응답 | 미구현; 기록 수준/보존 기간 결정 필요 |
| BREAK-01 | 쉬고 다시 돌아오기 | 계획 휴식·복귀 시각·수동 재개·종료 | 만료 상태 저장·background 실행·복구와 충돌 처리 | 복귀 성공/실패를 구분; 실패 구간 미집계; 무료 복구 유지 | 미구현; 지원 기간 실기기 선행 |
| OFFER-01 | 반복 관리의 편의에 지불 | 관련 맥락의 Pro 비교·정확한 가격·복원 | 검증된 추가 기능·상품/entitlement·StoreKit·심사 | 이해도·반복 사용·환불과 함께 전환 평가; 실패 시 무료 경로 유지 | 상품·가격 모두 가설; §4.10 검토 필요 |

## 13. 측정과 수용 기준

고객의 사용성·활성화·잔존·매출 측정값은 아직 없다. 시뮬레이터 코드 테스트 결과는 고객 성과와 별도다. 공통 활성화·반복 사용·매출 지표는 오케스트레이터와 메타데이터 정의를 그대로 사용한다. 아래는 UX 진단을 위한 구분이며 새로운 North Star를 만들지 않는다.

| 측정 단계 | 최소 기록 또는 관찰 | 오해를 막는 정의 |
|---|---|---|
| 유입→시작 | 상황별 메시지, 시작 여부 | 클릭률만으로 고객 만족을 판단하지 않음 |
| 권한 | 요청, 승인, 거부, 이후 복귀 | request 버튼 탭과 승인 분리; 개인 선택을 실패로 낙인찍지 않음 |
| 첫 작동 | 적용 시도, 고객 차단 확인, 복구 확인 | `isFocused == true`만으로 성공으로 계산하지 않음 |
| 규칙 | 저장, 활성 전환, 수정 취소, 삭제 결과 | 저장 개수 증가와 만족도 분리 |
| 반복 가치 | 유효 구간/완료, 회고, 다음 주 재사용 | 미응답·사용 중단·불명확 기록을 구분 |
| 회복 | 진입, 완료, 여전히 차단됨, 사유 선택 | 복구 횟수 감소만 최적화하지 않음; 복구를 숨기면 수치가 왜곡됨 |
| Pro | 노출 맥락, 가격 이해, 구매·취소·대기·복원 | 환불·해제 문제·이탈이 악화되면 전환 상승만으로 채택하지 않음 |

첫 사용성 라운드는 영어로 진행하는 글로벌 성인 8명, 네 상황별 2명으로 시작하는 안을 제안한다. 이를 통과한 뒤 세일즈 보고서의 상황별 8명·총 32명 제한 베타로 이어진다. 두 라운드는 목적과 표본을 구분한다. 서로 다른 영어 숙련도와 사용 환경을 포함하고 한국어 회귀 확인은 별도 진행한다. 다음 숫자는 **팀이 정하는 초기 수용 기준**이며 업계 평균이나 통계적 효과 증거가 아니다.

1. 같은 빌드에서 최소 7/8명이 도움 없이 첫 작동·복구 확인을 완료하고, GPS와 NFC의 차이를 자신의 말로 설명한다. 중간 수정한 서로 다른 빌드의 성공 인원을 합산하지 않는다. 현재는 기존 GPS/NFC 설정을 이용한 안내형 확인만 가능하며 새 수동 체험은 제공하지 않는다. 막힌 단계와 시간은 평균만 보고 숨기지 않는다.
2. 8/8명이 홈 상태와 무관하게 10초 이내 무료 복구 진입을 찾는다. 이는 발견 시간 기준이며, 10초 강제 대기 정책을 뜻하지 않는다. 실제 해제는 별도 확인한다.
3. 활성 전환·편집 취소·활성 삭제·NFC 일정 밖 스캔에서 의도하지 않은 제한 변경이 0건이어야 한다. 심각한 차단 잔존·복구 불능은 한 건이라도 해결 후 재검증한다.
4. 영어와 한국어에서 가장 큰 접근성 글씨·VoiceOver·Reduce Motion으로 핵심 여정이 완료되고 가격·종료 시각·복구 버튼이 잘리지 않는다.
5. Pro 이해도 확인에서는 고객이 실제 청구액·기간·무료로 남는 기능·해지/복원 경로를 설명할 수 있어야 한다. 작은 표본에서 지불 의사나 잔존 상승의 통계적 승자를 선언하지 않는다.

검증 자료에는 기기/OS, 언어, 권한 상태, 실행 전후 조건, 관찰 결과와 재현 방법을 적는다. 스크린샷과 실기기 증거를 확보하기 전에는 앱스토어 접근성·작동성·자동 복귀 보장 문구를 확정하지 않는다. 이번 보고서 갱신에서는 해당 실기기 검증을 수행하지 않았다. R1 코드·시뮬레이터 검증과 남은 현장/접근성/사용성 게이트는 [R1 구현 상태](R1_IMPLEMENTATION_STATUS.md)에서 구분한다.

## 14. 다음 변경에서 협업하는 방식

세일즈가 새로운 약속을 제안하면 같은 ID로 UX에 **어느 화면의 어떤 행동으로 고객이 그 약속을 경험하는지** 확인을 요청한다. UX는 [APP_GRAPH.md](APP_GRAPH.md)의 구현 담당 경로와 실패·종료 경로를 확인하고, 미구현이면 실제 기능과 제안을 분리한다. 가격·노출이 복구나 반복 사용과 충돌하면 오케스트레이터가 사용자 방향·실제 근거·안전·활성화·사업 결과 순서로 조정한다.

UX 산출물의 완료는 예쁜 화면 수가 아니라 **판매 약속, 고객 행동, 구현 근거, 실패 복구, 측정 정의가 같은 ID로 이어지는 것**이다. 새 화면·모드·의존성은 이 연결에서 필요한 경우에만 추가한다.

## 15. 페이월 벤치마크 기반 네이티브 업그레이드 검토 — 2026-09-07

사용자의 “이 리포트 기반으로 우리 앱 업그레이드 시켜” 지시에 따른 구현 검토다. [페이월 벤치마크 §08–09](PAYWALL_BENCHMARK_REPORT.ko.md)를 `ContentView.swift`, `OnboardingProfile.swift`, `AccountViews.swift`, `AccountStore.swift`와 대조했다. **이 절의 증거는 코드 검사이며 실기기·VoiceOver·구매 성공 관찰이 아니다.** 본문의 이전 제안과 구현 상태가 충돌하면 이 절의 제한된 최신 범위를 따른다.

| ID | 고객 문제와 약속 | 실제 화면·행동 및 의존성 | 확인 범위와 남은 조건 |
|---|---|---|---|
| ACT-01 | 처음 무엇을 설정할지 모름 → 생활 목표에 맞는 출발점 | Work / Rest / Presence / Personal time → Morning / Afternoon / Evening 선택이 첫 규칙 이름·1시간 일정 초안에 반영된다. 이후 기존 권한·대상·장소·시간·검토 흐름으로 이어진다. | 목표와 일정 연결은 코드 확인. 새 체험 모드나 자동 차단 성공 보장은 추가하지 않았다. |
| ACT-01 | 저장만 했는데 차단됐다고 오해 → 저장과 활성화를 별도 결정 | 최초 저장 뒤 실제 규칙 이름·시간과 `Saved on this iPhone. Restrictions haven’t started.`를 보여주며 활성화는 별도 `activateRule` 호출이다. 기존 규칙 보유 고객은 환영 화면을 건너뛴다. | 저장 실패 시 검토 화면 유지. 온보딩 완료와 활성화 성공은 같은 사건으로 계산하지 않는다. |
| ACT-01 | 로그인 때문에 첫 사용이 막힘 → 목표를 저장할 때 계정 선택 | 계정 화면은 홈에서 다시 열 수 있으며 Firebase 설정이 있을 때 Apple 로그인을 제공한다. Firestore 저장 범위는 선택한 목표다. 앱 선택·장소·규칙 동기화를 약속하지 않는다. | Firebase 클라이언트 설정·콘솔 및 기기 인증 검증이 필요하다. Google 로그인은 이번 코드 범위에 없다. |
| OFFER-01 | 기능·가격·체험이 불명확 → 실제 상품만 비교 | RevenueCat 월간·연간 상품의 총액·주기, 자격에 따른 체험/도입가, 자동 갱신 설명, 복원·닫기·무료 유지 경로를 구현했다. 자격 미확인 시 무료 체험을 단정하지 않는다. | `RoomProOfferReady`는 현재 false이며 Pro 혜택·상품·키·문서 URL이 미설정이다. **구매 가능한 Pro 출시 완료가 아니다.** 현재 무료 기능을 새로 잠그지 않는다. |
| OFFER-01 | 결제를 반복 강요받음 → 관련 맥락에서 선택 | 실제 `isFocused` 상태를 관찰한 뒤 조건을 충족할 때 홈의 선택적 Pro 진입을 표시한다. `Not now`와 페이월 닫기는 재제안 상태를 로컬에 보존한다. 계정에서 다시 탐색할 수 있다. | `isFocused` 관찰은 고객이 실제 대상 차단·복구를 확인했다는 증거가 아니다. 현재 상품 게이트가 닫혀 실제 제안은 표시하지 않는다. |
| ALIGN-01 | 새 흐름에서도 같은 앱처럼 이해 → 기본 서체와 영어 원문 유지 | 기존 시스템 serif 제목, rounded 본문·행동, monospaced 브랜드·시간을 사용한다. 새 온보딩·계정·상품·서비스 오류의 영어 키와 한국어 번역이 추가됐다. Roomie의 가격 영역 상시 애니메이션이나 취소 죄책감 반응은 없다. | 리소스 추가는 코드 확인. 가장 큰 Dynamic Type, VoiceOver 및 양 언어의 실제 화면 검증은 별도 확인이 필요하다. |
| SAFE-01 | 결제·계정 실패 시 통제권 상실 → 무료 로컬 복구 유지 | 계정/구매 코드는 제한 엔진을 제어하지 않는다. 기존 최상단 `Restore access`, 로컬 저장 규칙과 실제 무료 해제 경로를 유지한다. | 계정·페이월 sheet를 닫아 기존 복구에 돌아갈 수 있다. 오프라인 실기기 해제 성공은 별도 검증한다. |

검토 중 전달한 네 가지 사항은 최종 소스에서 수정됐음을 확인했다. 최초 저장 화면의 보조 행동은 `Go to home`으로 바뀌어 활성화를 유료 선택처럼 표현하지 않는다. 설정 헤더·진행 표시는 온보딩 완료 여부도 사용하므로 마지막 규칙 삭제 후에도 새 규칙 편집의 취소·홈 복귀를 제공한다. 계정 화면은 `Goal on this iPhone`과 `Saved account goal`을 함께 표시해 다른 목표를 덮어쓰기 전에 구분할 수 있다. 주요 상품 버튼은 `RoomPrimaryActionStyle`의 label 안에서 프레임과 `contentShape`를 지정한다. 이는 코드 수정 확인이며 터치·화면·접근성의 실제 수행 검증을 대신하지 않는다.

재방문·유료 전환 개선은 아직 측정되지 않았다. 보고서 §09의 Firebase 분석 이벤트, 실제 무료 체험 청구 전환, 환불·구독 갱신 집계는 이 화면 코드만으로 완료되지 않는다. 다음 상품 연결에서는 실제 제공하는 Pro 혜택을 먼저 확정하고, 가격·기간·자격·복원 성공을 스토어 환경에서 검증해야 한다. 기존 무료 규칙 및 복구를 결제 수단으로 제한하지 않는 조건은 그대로 유지한다.

## 16. 구역·세션 알림과 일반 조기 종료권 검토 — 2026-09-07

사용자는 구역 진입·이탈, 차단 시작·종료 알림과 구매형 강제해제 페이월을 요청했다. 이 절은 변경 전 `ContentView.swift`, `AccountViews.swift`, `AppModel.swift`를 읽고 구현 담당에게 전달한 **UX 계약·검토 결과**다. 런타임 수정·빌드·실기기·스토어 구매 완료를 뜻하지 않는다. 이 절 작성 당시 Offzone은 이름 후보였으며, 이후 사용자가 Offzone/오프존으로 제품명 변경을 확정했다. 외부 서비스 설정과 런타임 반영 결과는 별도 실행 기록을 따른다.

| ID | 고객 문제·약속 | 채택할 화면 행동·구현 의존성 | 검증·확신과 남은 조건 |
|---|---|---|---|
| CORE-01 | 구역 진입과 실제 차단 시작이 혼동됨 → 각각의 상태 변화를 확인 | 설정에서 `Zone & session alerts`를 선택한 뒤 시스템 알림 허용을 요청한다. 거절해도 규칙 설정·차단을 계속 쓸 수 있다. 현재 활성 규칙의 구역 진입/이탈과 차단 전환을 별개 사건으로 계산한다. | 진입했어도 일정 밖이면 차단 시작을 알리지 않는다. 최초 상태 확인·앱 재실행·반복 콜백은 실제 새 진입으로 과장하지 않는다. OS가 알림을 지연·억제할 수 있어 즉시 수신 보장은 하지 않는다. |
| CORE-01, RULE-01 | 알림이 너무 많거나 민감한 선택이 노출됨 → 짧은 규칙 맥락 | 로컬 알림은 규칙 이름과 상태로 제한하고 선택 앱/사이트 목록을 넣지 않는다. 같은 전환의 중복 알림을 제거한다. NFC는 세션 시작·종료만 알리며 구역 진입·이탈을 추측하지 않는다. | 실제 기기에서 일정 전환·위치 경계 왕복·권한 거절·오프라인·앱 재시작을 확인해야 한다. 한 번에 한 규칙, GPS 150m 한계는 그대로다. |
| OFFER-01 | 충동적인 일반 조기 종료 → 일회성 해제권의 결과·비용 확인 | 홈의 일반 `End session`은 현재 세션을 끝내는 일회성 해제권 sheet로 연결한다. 15분 후 재차단이나 구독을 이 상품처럼 설명하지 않는다. 실제 스토어 상품·가격·검증이 없으면 `Unlock passes aren’t available yet.`를 표시하고 구매 행동은 사용할 수 없게 한다. | 구매 가능한 출시 상태가 아니다. 가짜 가격·구매 성공·임시 entitlement를 만들지 않는다. 향후 실제 판매 전 활성화 단계에도 일반 조기 종료 비용과 무료 복구를 설명해야 한다. |
| SAFE-01 | 위치 오판·긴급 상황·구매 장애 → 계정·결제와 무관하게 복구 | 상단과 해제권 sheet에서 `Emergency & help` → 무료 `Restore access now`를 발견할 수 있어야 한다. 대기·사유 증명·죄책감 반응을 요구하지 않는다. | 일반 종료와 복구의 명칭을 구분하되 무료 복구를 숨기지 않는다. 결제 실패·네트워크 오류·상품 미설정 상태에도 이용 가능해야 한다. |
| RULE-01, OFFER-01 | 다른 행동으로 기존 세션이 예고 없이 종료됨 → 같은 결과는 같은 확인 | 기존 `End NFC session`, 활성 규칙 삭제, 다른 규칙 활성화 및 `Apply changes`는 현재 차단을 종료·교체할 수 있다. 집중 중에는 이 경로도 조기 종료 안내를 거치게 한다. 편집·저장 및 비활성 규칙 삭제는 유지한다. | `activateRule`/`deleteRule`/`endNFCFocus` 호출을 함께 검토해야 한다. 구매 뒤 대기 중이던 삭제·교체를 몰래 실행하지 말고 현재 세션을 끝낸 뒤 명시적으로 다시 선택하게 한다. 무료 복구·OS 권한 철회를 유료 우회 방지라는 이유로 막지 않는다. |
| ACT-01, RULE-01 | 앱만으로 NFC가 가능한지 불명확 → 필요한 물건과 동작 설명 | GPS는 별도 태그 없이 사용한다. NFC는 준비된 별도 물리 태그를 책상 등에 두고 앱에서 스캔을 시작한 뒤 iPhone을 가까이 대는 방식이다. | 현재 코드는 `https://roomdns.app/focus/<tag-id>` 형식만 검증하며 태그 쓰기·태그별 규칙 연결은 없다. 어느 준비된 태그든 활성 규칙을 시작할 수 있다. 자동 방 감지·자동 퇴실 해제를 약속하지 않는다. |

짧은 영어 원문과 한국어 현지화 제안:

| English | 한국어 | 사용 조건 |
|---|---|---|
| Zone & session alerts | 구역·세션 알림 | 설정 진입 |
| Get updates when your zone or blocking status changes. | 구역이나 차단 상태가 바뀌면 알려드려요. | 선택적 알림 설명 |
| Entered %@ / Left %@ | %@ 구역에 들어왔어요 / %@ 구역에서 나왔어요 | 실제 GPS 상태 변화 |
| Blocking started / Blocking ended | 차단이 시작됐어요 / 차단이 끝났어요 | 정책 적용 상태 전환 |
| End session | 세션 종료 | 일반 조기 종료 진입 |
| Unlock this session | 이번 세션 해제 | 일회성 상품 제목 |
| Unlock passes aren’t available yet. | 해제권은 아직 구매할 수 없어요. | 상품 미설정 상태 |
| No payment will be taken. | 결제되지 않아요. | 구매 불가 상태 |
| Keep session running | 세션 계속하기 | 페이월 닫기 |
| Emergency & help | 긴급 해제·도움 | 상시 무료 복구 진입 |
| Restore access now | 지금 차단 해제 | 무료 복구 실행 |
| NFC needs a separate tag. GPS works without one. | NFC는 별도 태그가 필요해요. GPS는 태그 없이 사용할 수 있어요. | 장소 방식 선택 |

기존 기본 서체와 정적인 Roomie를 재사용한다. 차단·복구 의미는 텍스트로 완성하고, 가격 영역의 모션이나 취소 시 실망 반응으로 구매를 압박하지 않는다. 해제권 전환율과 함께 해제 실패·환불·권한 철회·재방문을 확인하며, 이번 검토로 개선 수치를 주장하지 않는다. 가장 큰 Dynamic Type·VoiceOver 및 실제 알림 전달·소비형 결제 검증은 구현 담당의 별도 완료 조건이다.

## 17. 차단 중 앱 삭제 방지 검토 — 2026-09-07

사용자는 전체 화면 잠금·앱 삭제 방지와 구매를 통한 해제를 추가로 요청했다. 구현 담당이 선택한 최소 범위는 **규칙별 선택형 앱 삭제 방지**다. 전체 iPhone 잠금이나 결제만으로 해제할 수 있는 장치로 설명하지 않는다. 이 절은 변경 전 공통 제한 적용·해제 경로와 현재 해제권 화면에 대한 UX 검토이며 기능 구현·실기기 성공을 인증하지 않는다.

Family Controls 승인이 있는 앱에서 `ManagedSettingsStore.application.denyAppRemoval`로 앱 삭제를 제한할 수 있다는 Apple 엔지니어 답변이 있다. 이는 특정 앱만 삭제하지 못하게 하는 화면 잠금 약속과 구분한다. [Apple Developer Forums](https://developer.apple.com/forums/thread/729637). 개인 사용자는 설정에서 Screen Time 앱 권한을 철회할 수 있으므로 우회 불가능한 잠금으로 판매할 수 없다. [Apple WWDC22 Screen Time API](https://developer.apple.com/videos/play/wwdc2022/110336/).

| ID | 고객 문제·약속 | 최소 UX·구현 계약 | 남은 검증 |
|---|---|---|---|
| RULE-01, CORE-01 | 차단 앱을 충동적으로 삭제해 규칙을 피함 → 본인이 선택한 추가 마찰 | 규칙 검토에 기본 꺼짐 토글을 둔다. 모든 앱의 삭제에 영향을 준다는 설명을 토글 가까이에 표시한다. 저장과 실제 적용은 기존처럼 구분하며 단지 활성 규칙이 있다는 이유만으로 삭제를 막지 않는다. | 실제 `shouldShield`가 true이고 규칙이 명시적으로 선택한 경우에만 설정. 기존 규칙 필드 누락은 꺼짐으로 읽고 새 규칙에 이전 규칙의 선택을 암묵적으로 복사하지 않는다. |
| CORE-01, SAFE-01 | 일정·구역이 끝났는데 삭제 제한이 남음 → 차단과 함께 끝남 | 공통 `apply`에서 적용하고 `clearAllSettings`로 해제한다. 일정 종료·구역 이탈·NFC 종료·무료 복구·권한 오류·상태 저장 오류가 같은 해제 경로를 사용해야 한다. | 설정이 false/nil인 규칙을 적용할 때도 이전 true를 명시적으로 해제해야 한다. 다른 Screen Time 앱이 적용한 제한까지 해제됐다고 약속하지 않는다. |
| SAFE-01, OFFER-01 | 해제를 결제로만 얻는다고 오해 → 비용과 통제권을 이해 | 일반 조기 종료는 기존 해제권 화면으로 연결하되 상품 미설정·검증 미완료 동안 구매는 불가로 표시한다. 같은 화면의 무료 복구를 유지하고 복구 결과에는 앱 삭제 제한 해제도 포함한다. | `UnlockPassView`의 사용 불가 문구·무료 복구는 소스 확인. 실제 소비형 상품·검증된 차감·기기 해제 성공은 별도다. |

최소 영어 문안과 한국어 현지화 제안:

| English | 한국어 |
|---|---|
| Prevent app deletion during blocking | 차단 중 앱 삭제 방지 |
| Prevents deleting any app while this rule is blocking. | 이 규칙으로 차단 중에는 모든 앱의 삭제를 막아요. |
| You can turn off Screen Time access in iPhone Settings. Emergency recovery is free. | iPhone 설정에서 스크린 타임 접근을 끌 수 있어요. 긴급 복구는 무료예요. |
| App deletion protection | 앱 삭제 방지 |
| On during blocking / Off | 차단 중 켜짐 / 꺼짐 |

`Unbreakable`, `Lock your entire phone`, `Only payment can unlock` 및 `Only Offzone cannot be deleted`는 제공 범위와 맞지 않는다. 토글은 기존 시스템 서체·Dynamic Type·표준 접근성 동작을 재사용한다. 실기기에서 삭제 제한 켜짐과 해제, 기존 규칙 복원, 설정 권한 철회, 정상 종료·오류 복구를 확인하기 전에는 출시 가능 상태로 올리지 않는다.

D-12 실행 확인: 위 일반 종료·활성 교체/삭제 경로와 알림 설정을 실제 SwiftUI에 반영했다. 해제권 화면의 무료 복구는 하단 고정이며 시뮬레이터에서 복구 후 홈 전환을 확인했다. 실제 과금·실기기 알림은 미검증/미완료다. [구현·검증 범위](ZONE_UPGRADE_STATUS.md).

## 18. 해제권 클라이언트 상태 검토 — 2026-09-08

D-18은 실제 소모성 상품과 PASS 잔액을 읽고, 구매 완료 뒤 별도 확인으로 현재 세션의 해제권 1회를 사용하는 클라이언트 경로를 추가했다. 구매 취소·승인 대기·오류·잔액 없음·동일 요청 재시도·이전 세션 응답을 구분하며, 서버 차감 ID는 Firebase UID·런타임 generation·현재 일정 회차에 묶인다. 구매 성공만으로는 차단을 해제하지 않는다.

`RoomUnlockPassReady=false`가 Pro와 독립적으로 유료 호출을 막는다. 모든 상태에서 `Keep my session`과 무료 긴급 복구는 사용할 수 있다. 늦은 차감 확정의 보상/정리 정책과 실제 가격·StoreKit·계정 전환·환불·실기기 해제가 검증되기 전에는 이 게이트를 열지 않는다. 코드·시뮬레이터 테스트 확인이며 구매 성공이나 출시 가능 판정이 아니다.

## D-22 / OFFER-01 · INSIGHT-01 — 2026-09-09
Pro includes app-owned weekly intention planning and private, self-reported daily reflections with weekly counts. This is not automatically measured Screen Time, focus duration or saved time. New writes require a verified current-account Pro entitlement that has not expired. Reading, deleting and exporting existing local records remain available after expiry or sign-out; no cloud sync or backup is promised. The implemented UI is in Account & plan → Plan & reflect. Sales flags remain disabled pending pricing, real purchase validation and product review. Pass USD0.99 was approved and saved; Pro monthly/annual price is not yet confirmed. See `_workspace/PRO_IMPLEMENTATION_STATUS.md` for checks and limitations.
