# Product

## 2026-09-25 Android 우선 개발·화면 동기화 (D-46)

앞으로 Android를 주 개발 기준으로 삼는다. iOS에서 확정한 Nook/버터·따뜻한 아이보리/차콜 화면과 목표·시간 선택, 단계별 첫 규칙 작성, 저장 후 준비, 활성화 성공 뒤 닫을 수 있는 Pro 안내를 Android에 연결한다. 영어 원문·한국어 현지화, 명시적 Start focus, 무료 Restore access를 유지한다. Android Accessibility/앱 패키지 선택과 iOS Screen Time/앱·웹 토큰 선택은 운영체제에 맞게 동작한다. 판매 준비 플래그가 꺼진 동안 가격·체험·구매를 약속하지 않는다.

## 2026-09-25 첫 규칙 시간 선택과 Pro 진입 (D-45)

첫 설정의 시간 선택에는 아침·오후·저녁 1시간 제안 외에 **Another time**(직접 시작 시각 선택, 1시간 구간)과 **Not sure yet**(목표에 따른 추천 1시간 구간)을 둔다. 저장한 첫 규칙의 `Activate my rule`이 성공하면 닫을 수 있는 Pro 화면을 즉시 보여준다. `Continue with free` 또는 닫기로 홈에 남을 수 있고 무료 `Restore access`는 유지된다. 활성화 실패 때는 Pro 화면으로 이동하지 않는다. 참고 영상의 7일 체험이나 가격은 실제 자격·StoreKit 상품 확인 없이 약속하지 않으며 현재 판매 플래그는 꺼져 있다. 이 흐름은 iOS 코드에 반영됐고 Android 동등 흐름과 실기기/구매 검증은 남아 있다. 아래 예전 첫 사용·통행권 시퀀스보다 이 결정과 2026-09-16의 명시적 Start focus 흐름이 우선한다.

## 2026-09-25 Nook Cat 최종 선택·앱 적용 (D-44)

사용자가 [귀요미 10종 디자인보드의 F, Nook Cat](.growth-design/mascots/2026-09-25-cute-10x10/index.html#candidate-F)을 최종 마스코트로 골랐다. 크림색 털·짙은 녹색 눈·니트 조끼의 정체성을 유지한 새 표정8/전신4 정적 PNG를 iOS/Android 앱 상태에 연결한다. 기준 시안의 장면 배경은 앱 자산이 아니며 [Nook 앱 팩](.growth-design/mascots/2026-09-25-nook-native/MASCOTS.md)에 출처·해시·품질 한계를 기록한다. 영어 우선·한국어 보조, 명시적 Start focus, 항상 무료 Restore access, 공식 `off` 아이콘을 유지한다. Rue 의상3종과 아래 D-43은 당시 기록이며 Nook 옷장·획득·판매를 뜻하지 않는다. 시뮬레이터/에뮬레이터 화면 확인과 실기기·접근성·출시 검증을 구분한다.

## 2026-09-24 Rue v6 고정·의상 아바타 방향 (D-43, 과거 결정)

사용자가 **성인 Rue v6를 당시 Offzone 마스코트로 고정**하고 기존 Kiwi를 앱에서 모두 교체하도록 지시. iOS/Android 로컬 코드의 Kiwi 정지·영상 리소스를 Rue 표정8/전신4 정지 컷으로 대체해 빌드·시뮬레이터 첫 화면 확인; 이전 Kiwi 원본은 역사 보존. [새 의상 3종 비교](.growth-design/mascots/2026-09-24-rue-wardrobe/index.html)와 [아바타 착장/보상/구매 설계](.growth-design/mascots/2026-09-24-rue-wardrobe/WARDROBE_PLAN.md)는 **당시 제안**이며 당시 앱은 기본 Rue 의상만 보여줬다. 의상 아이템 소유·장착·보상·유료 판매/가격/상품·영수증 검증은 구현/승인되지 않았다. 무료 기본 룩·명시적 Start focus·무료 Restore access를 유료/보상 조건과 절대 묶지 않는다. 실기기 QA/사용권·원본성/투명 컷아웃·스토어/출시는 여전히 미완료. 현재 마스코트는 D-44 Nook Cat이고 2026-09-16의 수동 시작 결정은 유지한다.

## 2026-09-16 사용자 확정 — 장소 기반 수동 시작 / NFC 제거

CORE-01 / RULE-01 / ACT-01 / SAFE-01: 기존 Apple 지도에서 장소 검색·핀 조정, 오차를 고려한 주변 감지 후 사용자가 Start focus로 확정한다. 도착만으로 자동 차단하지 않으며, 새 회차·이탈·복구 후에는 다시 시작해야 한다. NFC 기능·UI·권한은 제거하고 기존 규칙 데이터는 편집용으로 보존한다. 방/책상 수준 위치 정확도를 약속하지 않는다. 영어 기본·한국어 보조 및 유료 판매 비활성 유지. 아래 NFC/자동 시작 관련 과거 계획보다 이 결정을 우선한다. 구현/검증 기록은 `_workspace/PLACE_FOCUS_IMPLEMENTATION.md` 참조.

## Register

product

## Current Strategy Contract — G1

현재 업그레이드 실행 계획: [APP_UPGRADE_PLAN.ko.md](APP_UPGRADE_PLAN.ko.md), U1. 신뢰·첫 사용 → 반복 가치 → 상품 검증 순서와 단계별 완료 조건을 따른다. 아래 과거 Delivery Plan은 G1/U1에서 채택한 범위로 해석한다.

2026-09-06 사용자 결정: **영어가 고객용 메인 언어이며, 글로벌 디지털 자기관리 고객 전체가 대상이다.** 학생·한국 시장을 우선 타깃으로 한 과거 판매 가설은 대체한다. 업무, 휴식, 사람과 함께하는 시간, 개인 목표를 모두 다룬다. 학습은 사용 사례 중 하나다.

공통 결정·우선순위는 [ORCHESTRATOR.md](ORCHESTRATOR.md), 판매 제안은 [세일즈 전략](SALES_STRATEGY_REPORT.ko.md), 고객 경험은 [UX 전략](UX_STRATEGY_REPORT.ko.md), 현재 코드와 제안 의존성은 [앱 구성 그래프](APP_GRAPH.md), 구현 상태는 [메타데이터](APP_METADATA.json)로 연결한다. 최신 사용자 결정이 이전 제안보다 우선하며, 실제 구현 여부는 코드·검증 결과로 판단한다.

이 문서의 통행권·RevenueCat·소비형 상품 세부사항은 **기존 미구현 가설의 설계 참고**다. 현재 Pro 제안과의 선택은 `OFFER-01`에서 조율하며, 과금 구현을 자동 승인하는 지침이 아니다. 아래 과거 한국어 고객 문안도 참고용이며 새 고객 문안 원본은 영어로 작성한다. R1에서 `developmentRegion = en` 및 영어 소스 키를 반영하고 한국어 리소스를 유지했다. 저장/적용 분리·삭제·상시 무대기 무료 복구의 현재 상태와 검증 한계는 [R1 실행 기록](R1_IMPLEMENTATION_STATUS.md)을 따른다. 아래 과거 흐름·와이어프레임이 이 기록과 다르면 현재 코드 기준 설명으로 사용하지 않는다.

## Product Snapshot

- 확정 앱 이름: Offzone / 오프존 (2026-09-07)
- 제품 형태: iPhone 네이티브 앱, SwiftUI, 최소 iOS 18
- 대상: 자신의 디지털 습관과 시간을 스스로 관리하려는 글로벌 성인 고객. 직업·학생 신분으로 한정하지 않는다.
- 메인 언어: 영어. 한국어는 보조 현지화이며 내부 전략 보고서는 한국어로 운영할 수 있다.
- 고객 약속 초안: "Make room for what matters."
- 핵심 약속: 정한 장소·시간 조건을 확인한 뒤 사용자가 명시적으로 Start focus를 시작해 산만한 앱과 웹사이트를 가리고, 집중 약속을 지키도록 돕는다.
- 공간 전략: GPS는 집, 회사, 도서관 같은 장소를 확인하는 데 사용한다. NFC 실행은 현재 제품 흐름에서 제거했다.
- 규칙 전략: 이름 붙인 규칙을 여러 개 저장하고 홈에서 목록으로 관리한다. MVP에서는 한 번에 한 규칙만 활성화해 실제 차단 상태와 화면이 어긋나지 않게 한다.
- 현재 마스코트: 크림색 고양이 `Nook Cat`. 상태별 표정과 니트 조끼 전신으로 화면을 보조하며 실제 정책 상태·행동은 텍스트와 버튼으로 명확하게 전달한다.
- 기존 MVP 수익 가설: 사전 동의한 집중 모드의 15분 통행권 1종. 미구현이며, Pro 자동화·관리·기록 제안과 함께 `OFFER-01`에서 후속 판단한다.
- 안전 원칙: 위치 오판, 결제 장애, 긴급 상황을 위한 무료 Restore access는 항상 제공한다. 필수 앱 예외는 아직 구현되지 않아 별도 설계·검증이 필요하다.

## Users

### Primary user

업무 중, 쉬는 시간, 사람들과 함께 있을 때, 자기만의 시간을 보낼 때 SNS·숏폼·커뮤니티·뉴스·쇼핑 앱을 습관적으로 여는 성인이다. 스스로 규칙을 정할 의향은 있지만 충동이 시작된 순간에는 멈추기 어렵다. 영어로 제품을 사용하는 글로벌 고객을 기준으로 설계하며 학생은 여러 고객 중 하나다.

### Contexts, not demographic restrictions

| ID | English label | 원하는 결과 |
|---|---|---|
| work | Work | 중요한 일에 주의를 유지 |
| rest | Rest | 쉬기로 정한 시간에 스크롤을 줄임 |
| presence | Time with people | 함께 있는 사람에게 주의를 돌림 |
| personal | Personal time | 독서·창작·학습 등 스스로 선택한 시간 보호 |

### Core jobs

- "서재에 들어오면 별도의 결심 없이 산만한 앱이 닫혀 있기를 원한다."
- "집중 중 잠깐 흔들릴 때, 무의식적으로 해제하지 않고 한 번 더 생각하고 싶다."
- "왜 막혔는지, 언제 풀리는지, 지금 시스템이 정상인지 즉시 알고 싶다."
- "내 위치와 선택 앱 정보가 외부에 수집되지 않기를 원한다."

### Not the user

- 자녀나 직원을 원격 감시하려는 사람
- 다른 사람의 기기를 강제로 통제하려는 조직
- 중독 치료나 의료적 개입을 기대하는 사람
- 방마다 별도 하드웨어를 대량 설치하려는 시설 운영자

## Product Purpose

Offzone은 통제 자체를 판매하지 않는다. 사용자가 평온한 순간에 세운 약속이 충동적인 순간에도 유지되도록 환경을 바꾼다.

제품이 최적화할 대상은 결제 횟수만이 아니다. 사용자가 지킨 집중 시간, 완료한 집중 세션, 권한 유지율, 장기 잔존이 먼저다. 통행권 매출이 늘어도 보호 시간이 줄거나 환불, 삭제, 권한 해제가 늘면 제품 실험은 실패다.

## Product Principles

1. 미리 약속하고 나중에 자동으로 실행한다.
2. 상태와 이유를 숨기지 않는다.
3. 마찰은 숙고를 돕기 위해서만 사용한다.
4. 위치 정밀도와 행동과학 근거를 과장하지 않는다.
5. 결제하지 않아도 기기 통제권과 안전은 사용자에게 남는다.
6. 가능한 한 기기 안에서 처리한다.

## Brand Personality

- 고요하지만 무기력하지 않다.
- 단호하지만 처벌하지 않는다.
- 성숙하고 절제되어 있다.
- 사용자의 선택을 존중한다.
- 과학을 장식용 권위가 아니라 검증 방법으로 사용한다.

권장 카피의 결은 짧고 구체적이어야 한다.

- 좋음: "서재 규칙이 작동 중이에요. 14:32에 다시 열립니다."
- 좋음: "15분만 문을 열고 자동으로 돌아올까요?"
- 피함: "또 실패하셨네요."
- 피함: "지금 결제하지 않으면 집중 기록이 사라져요."

## Anti-references

- 해킹 도구처럼 보이는 네온, 레이더, VPN, 방패 그래픽
- 자물쇠와 감옥을 반복하는 위협적 표현
- 나무 키우기, 불꽃 스트릭, 실패 시 캐릭터가 죽는 죄책감 게임화
- 기업용 MDM 대시보드처럼 복잡한 표와 설정
- 가짜 카운트다운, 가짜 할인, 근거 없는 인기 배지
- 닫기 버튼 은닉, confirm-shaming, 반복되는 이탈 제안
- 상위 앱의 화면, 카피, 일러스트, 브랜드를 그대로 복제하는 방식
- "방을 정확히 감지", "중독 치료", "과학적으로 보장" 같은 과장

## Design Principles

1. 홈은 현재 상태 한 가지를 답한다: 밖, 준비 중, 집중 중, 잠시 열림, 조치 필요.
2. 각 화면에는 질문 하나와 행동 하나만 둔다. 기능은 설명문보다 다음 행동으로 익히게 한다.
3. 시각적 변화는 문턱을 넘는 공간 변화로 표현한다.
4. 집중 중 화면은 정보를 줄이고 종료 시각과 적용 장소를 크게 보여준다.
5. 결제 화면은 무엇을 얼마 동안 여는지와 실제 가격을 가장 먼저 보여준다.
6. 색, 모션, 햅틱만으로 상태를 전달하지 않는다.

### Visual direction

- 기본 배경: 따뜻한 미색과 잉크색
- 집중 강조색: 차분한 코발트 또는 청록 한 색
- 경고색: 오류와 안전 해제에만 제한적으로 사용
- 대표 그래픽: 통통한 흰 방 정령 `룸이`와 발 아래 짧은 코발트 문턱
- 밖: 선과 입자가 느슨하게 흩어진 상태
- 진입: 룸이가 몸을 세우고 코발트 문턱이 정렬되는 전환
- 집중 중: 룸이가 넓고 낮게 가라앉아 거의 움직이지 않고 정확한 종료 시각을 보여준다.
- 15분 통행권: 룸이는 중립을 유지하고 코발트 문턱 길이만 남은 시간에 따라 줄어든다.
- Reduce Motion에서는 모든 공간 변형을 짧은 교차 페이드와 텍스트 상태 변경으로 대체한다.

## Accessibility & Inclusion

- Dynamic Type의 접근성 크기에서도 주요 행동과 가격이 잘리지 않는다.
- VoiceOver 순서는 상태, 장소, 종료 시각, 주요 행동, 보조 행동 순이다.
- 모든 아이콘과 그래픽에 의미 있는 접근성 레이블을 제공하거나 장식으로 숨긴다.
- 텍스트와 핵심 컨트롤은 WCAG AA 수준 대비를 목표로 한다.
- 터치 영역은 최소 44 x 44pt다.
- 색만으로 차단 여부, 결제 성공, 오류를 구분하지 않는다.
- Reduce Motion, Reduce Transparency, Bold Text를 존중한다.
- 자유 입력을 강요하지 않는다. 해제 사유는 선택형이며 건너뛸 수 있다.
- 영어를 UI·온보딩·스토어·지원 문안의 기준 언어로 삼고 한국어를 보조 지원한다. 긴 영어 문장, 날짜·12/24시간 표기, 현지화 가격과 접근성 글자 크기를 함께 검증하며 고정 폭을 피한다.

## Core Product Decision

### Room-level detection

GPS 지오펜스는 건물과 장소 단위다. 같은 건물 안의 침실과 서재를 안정적으로 구분한다고 약속하지 않는다.

MVP는 다음 두 신호만 사용한다.

1. GPS Place: 집, 회사, 학교, 도서관 같은 장소 진입과 이탈을 자동 감지한다.
2. NFC Focus Spot: 방이나 책상에 붙인 태그를 사용자가 탭해 집중을 시작한다.

iBeacon은 2단계 실험이다. 방마다 비콘이 필요하고 벽, 문, 사람, 기기 방향에 따라 신호가 흔들리므로 실기기 오탐률이 허용 범위에 들어올 때만 제품화한다. UWB와 전용 액세서리는 MVP 범위가 아니다.

### Blocking model

- FamilyControls의 개인 권한으로 사용자가 직접 앱, 카테고리, 웹 도메인을 선택한다.
- ManagedSettings로 선택 대상을 shield 처리한다.
- DeviceActivity와 Core Location 이벤트를 조합해 일정과 장소 상태를 계산한다.
- 사용자가 앱을 삭제하거나 Screen Time 권한을 철회하면 우회할 수 있다. "절대 해제 불가"라고 약속하지 않는다.
- 모든 제한 상태는 하나의 결정 함수에서 계산하고, 하나의 멱등적인 `reconcileRestrictions()` 경로로 적용하고 해제한다.

### Two exit paths

일반 해제와 안전 해제를 명확히 분리한다.

#### 15-minute pass

- 사용자가 규칙 생성 시 "집중 중 일반 해제에는 15분 통행권 구매가 필요함"에 명시적으로 동의한 경우에만 제공한다.
- 한 번 구매하면 선택한 차단 대상을 15분 동안 열고 자동으로 다시 막는다. 재차단 시각은 DeviceActivity의 실제 동작을 실기기에서 검증한 뒤 출시 약속으로 확정한다.
- 활성 통행권 중에는 중복 구매를 막는다.
- 통행권 화면에는 열리는 대상, 다시 막히는 시각, 실제 일회성 가격, 자동 갱신 없음이 보인다.
- 가격과 구매 버튼은 메인 앱에서만 표시한다. Shield extension에는 상품 판촉이나 IAP를 넣지 않는다.

#### Free safety release

- 위치 오판, 급한 업무, 결제 또는 네트워크 장애에 대비해 항상 보인다.
- 10초의 명확한 숙고 뒤 표준 버튼으로 현재 규칙을 무료로 종료한다.
- 지연 시간과 결과를 시작 전에 그대로 설명한다.
- 안전 해제를 사용해도 스트릭 손실, 비난 문구, 추가 결제가 없다.
- 안전 해제율은 제품 오류와 규칙 품질을 찾는 지표이며, 사용자 처벌 수단이 아니다.

## Core Experience

### First-run flow

1. 친근한 한 문장 가치 제안: "장소가 집중을 기억해요."
2. Screen Time 권한을 요청하는 이유를 설명한 뒤 시스템 권한을 요청한다.
3. 시스템 선택기로 가릴 앱과 웹사이트를 고른다.
4. 현재 위치로 첫 GPS Place를 만들거나 NFC Focus Spot을 선택한다.
5. 시작 시각과 종료 시각을 정한다.
6. 대상, 장소, 시간을 한 화면에서 확인하고 첫 규칙을 저장한다.
7. 저장 즉시 현재 상태가 보이는 홈에 도착한다.

첫 설정 뒤 홈은 앱의 중심 화면이 된다. 상단에서 현재 집중 상태를 확인하고, 아래의 `내 규칙` 목록에서 규칙을 추가·편집하거나 `사용` 버튼으로 활성 규칙을 바꾼다.

온보딩 중에는 구매를 요구하지 않는다. 사용자는 첫 규칙 생성과 실제 작동 경험을 먼저 완료한다.

### Active focus flow

1. 장소, 일정, NFC 중 필요한 조건이 충족된다.
2. 홈의 룸이가 낮게 가라앉고 코발트 문턱이 집중 상태로 바뀐다.
3. 선택한 대상에는 중립적인 shield가 보인다.
4. shield는 집중 상태와 종료 시각만 설명한다. 결제 가격이나 구매 버튼을 노출하지 않는다.
5. 사용자가 규칙 관리를 원하면 Offzone 메인 앱을 직접 연다.
6. 메인 앱에서 6초 동안 현재까지 지킨 실제 시간을 보여준다.
7. "계속 집중"과 "15분 통행권 보기"를 동일하게 이해 가능한 선택으로 제공한다.
8. 통행권 구매가 끝나면 종료 시각을 먼저 저장하고 제한 상태를 재조정한다.
9. 15분 뒤 자동으로 다시 차단한다.

### Paywall sequence

통행권 화면은 놀라게 하는 덫이 아니라 사용자가 사전에 선택한 약속을 실행하는 화면이다.

1. Context: "서재 규칙이 47분 동안 집중을 지키고 있어요."
2. Outcome: "Instagram과 YouTube를 15분 열고 14:37에 다시 닫습니다."
3. Product: StoreKit에서 받은 실제 지역화 가격과 "1회 결제, 자동 갱신 없음".
4. Legacy Korean primary-action example: "₩X로 15분 열기". G1에서 채택할 경우 영어 원문과 StoreKit 지역화 가격으로 새로 검토한다.
5. Non-purchase action: "계속 집중하기".
6. Safety action: "위치 오류 또는 긴급 상황인가요? 무료 안전 해제".
7. Purchase result: 성공, 취소, 대기, 실패를 구분하고 실패 시 제한을 임의로 바꾸지 않는다.

구매 버튼의 가격은 코드에 하드코딩하지 않는다. 실제 StoreKit 상품 정보가 없거나 불러오기 실패 시 구매 UI를 숨기고 무료 안전 해제를 유지한다.

## MVP Scope

### Included

- iOS 네이티브 SwiftUI 앱
- FamilyControls 개인 권한 요청
- 앱, 카테고리, 웹 도메인 선택
- GPS Place 또는 NFC Focus Spot과 시간 구간을 가진 규칙 프리셋 여러 개
- 한 번에 활성 규칙 1개
- 현재 상태, 저장한 규칙 목록, 새 규칙 진입점을 보여주는 홈
- 앱과 웹 도메인 shield
- 60초 작동 테스트
- 15분 통행권 1종
- 무료 안전 해제
- 로컬 집중 세션 기록과 지난 7일 보호 시간
- VoiceOver, Dynamic Type, Reduce Motion 기본 대응
- 영어 우선·한국어 보조 로컬라이징 (현재 소스 키·개발 언어 마이그레이션은 별도 구현)

### Original MVP exclusions and pending product decisions

아래 제외 목록은 기존 통행권 MVP 기준이다. Pro 구독 검토는 새 `OFFER-01` 제안이며 확정 전에는 구현하지 않는다. 영어 우선·글로벌 타깃 결정은 과금 모델을 확정한 것으로 해석하지 않는다.

- Android
- iBeacon 자동 방 감지
- UWB와 전용 액세서리
- 계정, 서버, 소셜 기능, 여러 기기 동기화
- 통행권 묶음, 잔액, 선물, 만료, RevenueCat Virtual Currency
- 구독과 lifetime 상품
- AI 코칭
- 원격 자녀 보호 또는 직원 관리
- 임의 URL 문자열을 검사하는 VPN 방식
- 커스텀 규칙 엔진과 자체 디자인 시스템 패키지
- 여러 규칙의 동시 실행과 겹침 우선순위

## RevenueCat Specification

### Product setup

- App Store product ID: `roomdns.break.15m`
- StoreKit type: consumable
- RevenueCat Offering: `break_pass`
- RevenueCat custom package: `fifteen_minute`
- Entitlement: 연결하지 않는다. Consumable을 entitlement에 연결하면 영구 기능처럼 보일 수 있다.
- Legacy pre-G1 price hypothesis (비활성): 한국 스토어 기준 약 ₩1,100. 현재 글로벌 가격·상품 결정은 OFFER-01에서 검토하며 이 과거 값은 출시 기준이 아니다. 실제 상품 채택 시 App Store Connect 가격을 설정하고 앱은 StoreKit 지역화 값을 표시한다.

### Purchase semantics

- 상품은 구매 즉시 현재 세션에 사용되며 보관형 크레딧이 아니다.
- 구매 성공을 확인한 뒤 transaction ID, 시작 시각, 종료 시각을 App Group 저장소에 먼저 기록한다.
- 같은 transaction ID를 두 번 적용하지 않는다.
- 저장 성공 뒤 `reconcileRestrictions()`를 호출한다.
- 앱이 종료되어도 저장된 종료 시각까지 통행권 상태를 복원하고 이후 다시 차단한다.
- Consumable은 일반적인 구매 복원 대상이 아님을 구매 전에 알린다.
- 구매 직후 앱 종료 등으로 통행권이 적용되지 않은 경우를 위한 지원 경로를 제공한다.

### RevenueCat use

- 공개 SDK 키만 앱에 포함한다.
- Offering은 원격으로 가져오되 상품이 없을 때 무료 기능이 계속 동작한다.
- 구매 취소는 오류처럼 꾸짖지 않고 원래 집중 화면으로 돌아간다.
- 구매 대기와 실패에서는 제한을 임의로 해제하지 않는다.
- App Store 가격, 통화, 구매 확인 sheet를 모방하지 않는다.
- 활성 통행권이 있으면 Offering을 다시 표시하지 않는다.

### Why Virtual Currency is not in MVP

묶음 통행권 잔액은 서버 차감, 계정 복구, 중복 사용 방지가 필요하다. 첫 제품 가설을 검증하는 데 필요하지 않으므로 즉시 사용하는 consumable 1종으로 시작한다. 통행권 묶음 수요와 재구매가 확인된 뒤에만 계정과 서버를 포함한 별도 제품 결정을 한다.

## Evidence-informed Behavior Design

"과학적"은 효과를 보장한다는 뜻이 아니다. 가설, 측정, 반증 조건이 있다는 뜻이다.

### Adopted mechanisms

- Implementation intention: "서재에 들어오면 SNS를 가린다"처럼 상황과 행동을 미리 연결한다.
- Precommitment: 집중 전에 해제 조건을 사용자가 직접 정하고 확인한다.
- Brief friction: 충동 직후 6초의 짧은 정지와 "계속 집중" 선택을 제공한다.
- Time-boxing: 통행권은 15분 뒤 자동 종료되도록 예약한다.
- Truthful feedback: 실제 기기 이벤트로 계산한 보호 시간만 보여준다.
- Autonomy: 계속 집중, 통행권, 무료 안전 해제의 의미를 숨기지 않는다.

## Competitive Pattern Decisions

상위 앱을 그대로 복제하지 않고 반복해서 검증된 구조만 가져온다.

- one sec에서 채택: 앱을 열기 전 짧게 멈추고 "열지 않기"를 명확히 선택하게 하는 방식
- Opal과 Jomo에서 채택: 평상시 모드와 더 강한 사전 커밋 모드를 구분하는 방식
- Freedom에서 채택: 먼저 작동 경험을 주고 더 강한 제약이나 자동화를 나중에 제안하는 방식
- ScreenZen에서 채택: 한 화면에서 이해되는 단순한 규칙 설정
- 채택하지 않음: 타사의 정확한 온보딩 순서, 가격 프레이밍, 카피, 색, 그래픽, social proof

공개 자료에서 유료 해제를 핵심 모델로 검증한 상위 앱은 확인되지 않았다. 따라서 15분 통행권은 업계 표준이 아니라 Offzone의 고유한 제품 가설이며, 환불, 권한 철회, 보호 시간 악화를 포함해 검증한다.

### Claims policy

- 연구 결과는 연구 대상과 한계를 함께 표기한다.
- 타사 내부 설문과 마케팅 수치를 Offzone의 성과처럼 사용하지 않는다.
- 실제 사용 데이터가 없을 때 예상 절약 시간을 단정하지 않는다.
- 의료, 치료, 중독 완화 효능을 주장하지 않는다.

## Conversion Design

최적화 목표는 지속 가능한 순매출이다. 순간 구매율을 올리기 위해 신뢰와 집중 성과를 훼손하지 않는다.

### Conversion levers

- 사전 동의: 규칙을 만들 때 통행권의 가격 구조를 미리 알린다.
- Earned value: paywall 전에 현재 세션에서 실제로 지킨 시간을 보여준다.
- Concrete outcome: 15분 동안 열리는 대상과 다시 닫히는 시각을 적는다.
- Single product: 선택 피로 없이 통행권 한 종류만 보여준다.
- Honest scarcity: 세션당 활성 통행권 하나만 허용한다. 가짜 시간 제한은 쓰지 않는다.
- Low regret: 자동 갱신이 없고, 중복 구매가 안 되며, 취소가 쉽다.

### Prohibited conversion tactics

- shield 안에서 결제를 홍보하거나 구매시키기
- 무료 안전 해제를 숨기거나 일부러 찾기 어렵게 만들기
- 가격 로딩 실패 시 임의 가격 표시
- 사용자 수, 후기, 절약 시간 조작
- 구매하지 않으면 실패자라는 문구
- 스트릭 손실로 협박하기
- 종료 버튼을 늦게 나타나게 하기
- 결제 취소 직후 더 싼 제안을 반복하기

### Experiment plan

한 번에 변수 하나만 바꾼다.

1. H1: 6초 정지가 없는 버전보다 "계속 집중" 선택률이 높아진다.
2. H2: 실제 보호 시간을 보여주면 보여주지 않을 때보다 구매 후 후회 지표를 악화시키지 않고 구매 이해도가 높아진다.
3. H3: 정확한 재차단 시각을 보여주면 통행권 종료 전 추가 안전 해제가 줄어든다.
4. H4: 사전 동의 화면을 이해한 사용자는 환불, 삭제, 권한 철회율이 낮다.

표본이 작을 때 승자를 선언하지 않는다. 전환이 올라가도 D7 유지율, 보호 시간, 환불률, 권한 유지율 중 하나가 의미 있게 악화되면 채택하지 않는다.

## Data and Privacy

- 원시 좌표, Screen Time 토큰, 선택 앱 이름, 웹 도메인, 자유 입력 해제 사유를 분석 서버로 보내지 않는다.
- 위치 판정, 규칙, 통행권 종료 시각, 세션 기록은 기기와 App Group에 저장한다.
- RevenueCat에는 구매 처리에 필요한 식별자와 상품 이벤트만 전달한다.
- 계정이 없는 MVP는 익명 RevenueCat App User ID를 사용한다.
- 분석이 필요하면 장소 수, 규칙 수, 세션 길이 구간처럼 식별 불가능한 집계만 보낸다.
- 설정에서 로컬 기록 전체 삭제를 제공한다.
- 위치 권한 철회 시 위치 기반 규칙을 비활성화하고 남아 있는 shield를 정리한다.

## State Model

홈과 제한 엔진은 같은 상태를 사용한다.

- `outside`: 활성 장소 밖
- `armed`: 장소 안이지만 일정 또는 NFC 조건 대기
- `focused`: 제한 적용 중
- `passActive(until)`: 15분 통행권 적용 중
- `safetyReleasePending(until)`: 무료 안전 해제 숙고 중
- `needsAction(reason)`: 권한, 위치, 상품 또는 설정 문제

우선순위는 안전 해제 완료, 활성 통행권, 권한 유효성, 장소, 일정, NFC, 사용자 규칙 순으로 명시적으로 정의한다. 여러 이벤트가 동시에 오더라도 `reconcileRestrictions()` 결과가 같아야 한다.

## Success Metrics

### North-star behavior

- 주간 완료 집중 세션 수
- 주간 보호 시간

### Activation

- Screen Time 권한 완료율
- 첫 장소와 첫 규칙 생성률
- 60초 테스트 성공률
- 첫 실제 장소 진입 후 shield 성공률

### Business

- 통행권 화면 도달 대비 구매 완료율
- 구매자당 30일 재구매율
- 순매출과 환불률

### Trust guardrails

- 무료 안전 해제율과 사유 분포
- 위치 오탐 신고율
- 권한 철회율
- 앱 삭제 추정률
- 구매 실패와 미적용 지원 건수
- 통행권 구매 이후 집중 세션 완료율

## MVP Acceptance Criteria

1. 실제 iPhone에서 개인 FamilyControls 권한을 받고 선택한 앱과 웹 도메인을 shield 할 수 있다.
2. GPS Place 진입, 이탈이 foreground, background, terminated 상태에서 관찰되며 한계를 기록한다.
3. NFC 태그 탭으로 Focus Spot을 시작할 수 있고, 자동 방 감지라고 표현하지 않는다.
4. 앱 재실행과 기기 재부팅 뒤 현재 제한 상태를 다시 계산한다.
5. 권한 철회, 위치 오류, 규칙 삭제 뒤 stale shield가 남지 않는다.
6. 통행권 구매 성공 시 종료 시각을 저장한 뒤에만 15분 동안 제한을 푼다.
7. 구매 취소, 실패, 대기, Offering 없음에서 중복 결제나 임의 해제가 발생하지 않는다.
8. 무료 안전 해제는 네트워크와 RevenueCat 상태에 관계없이 동작한다.
9. 실제 StoreKit 가격과 일회성 조건을 구매 전에 표시한다.
10. VoiceOver, 접근성 글자 크기, Reduce Motion에서 전체 핵심 흐름을 완료할 수 있다.

## Delivery Plan

### Phase 0: capability spikes

- FamilyControls 개인 권한과 배포 entitlement
- 앱과 웹 도메인 shield 적용 및 해제
- DeviceActivity extension과 App Group 공유
- Core Location 지오펜스의 실제 백그라운드 동작
- NFC 태그 탭 흐름
- RevenueCat sandbox consumable 구매와 transaction idempotency

각 스파이크가 실제 기기에서 재현되지 않으면 제품 문구와 범위를 먼저 줄인다.

### Phase 1: one vertical slice

규칙 생성, 대상 선택, 장소와 일정 설정, 자동 shield, 규칙 목록과 활성 전환, 홈 상태, 무료 안전 해제를 끝까지 만든다.

### Phase 2: room intent and payment

NFC Focus Spot, 통행권 paywall, 15분 자동 재차단, 구매 오류와 지원 흐름을 추가한다.

### Phase 3: evidence and polish

지난 7일 보호 시간, Roomie 상태 모션, 접근성 검증, 한 변수 A/B 테스트를 추가한다.

### Later, only with evidence

- iBeacon 방 감지
- 여러 규칙의 동시 실행과 우선순위
- 통행권 묶음과 Virtual Currency
- Pro 구독
- 여러 기기 동기화

## Open Risks

- FamilyControls 배포 entitlement 승인이 출시 일정의 선행 조건이다.
- iOS의 위치 이벤트는 실내 방 구분과 즉시성을 보장하지 않는다.
- 앱이 종료된 활성 차단 중 위치 권한 철회가 즉시 전달되는지는 공개 API만으로 보장되지 않는다. 실기기에서 stale shield가 남으면 GPS 자동 차단 범위를 축소한다.
- NFC 태그는 자동 감지가 아니라 사용자의 의도적 동작이다.
- shield에서 메인 앱 paywall로 직접 이동시키는 공개 동작은 제한적이다.
- 사용자가 Screen Time 권한을 철회하거나 앱을 삭제하면 제한을 우회할 수 있다.
- 즉시 소비형 상품은 구매 직후 앱 종료 시 미적용 지원 위험이 있다.
- 통행권 매출은 사용자의 흔들림과 연결되므로 신뢰 지표를 함께 보지 않으면 잘못 최적화하기 쉽다.
- App Review가 pay-to-unblock 문맥을 조작적이라고 판단할 가능성이 있다. 무료 안전 해제, 사전 동의, 정직한 가격, 메인 앱 결제 원칙을 심사 노트에 명확히 설명해야 한다.

## References

- Apple Family Controls: https://developer.apple.com/documentation/familycontrols
- Apple Managed Settings: https://developer.apple.com/documentation/managedsettings
- Apple Device Activity: https://developer.apple.com/documentation/deviceactivity
- Apple geographic regions: https://developer.apple.com/documentation/corelocation/monitoring-the-user-s-proximity-to-geographic-regions
- Apple iBeacon proximity: https://developer.apple.com/documentation/corelocation/determining-the-proximity-to-an-ibeacon-device
- Apple background NFC tags: https://developer.apple.com/documentation/corenfc/adding-support-for-background-tag-reading
- Apple App Review Guidelines: https://developer.apple.com/app-store/review/guidelines/
- Apple In-App Purchase types: https://developer.apple.com/help/app-store-connect/reference/in-app-purchases-and-subscriptions/in-app-purchase-types
- RevenueCat non-subscription purchases: https://www.revenuecat.com/docs/platform-resources/non-subscriptions
- RevenueCat Offerings: https://www.revenuecat.com/docs/offerings/overview
- RevenueCat Virtual Currency: https://www.revenuecat.com/docs/offerings/virtual-currency
- one sec field study: https://pmc.ncbi.nlm.nih.gov/articles/PMC9974409/
- Opal App Store listing: https://apps.apple.com/us/app/opal-screen-time-control/id1497465230
- Jomo pricing: https://jomo.so/pricing
- Freedom free and premium comparison: https://support.freedom.to/en/articles/13764747-what-s-included-in-free-and-premium-plans
- ScreenZen: https://screenzen.co/


## Native Roomie design — 2026-09-07
사용자 승인 시안의 중앙 상태/Roomie, 종이색, 녹색 주요 행동, 가벼운 목록을 네이티브 앱에 적용했다. 사용자가 요청한 기존 시스템 서체를 유지한다. 규칙 저장과 활성화, 예약과 실제 집중, 복구와 오류를 구분한다. 48시간 뒤 인사는 로컬 방문 시각에 근거하며, 통계/육성/알림 기능이나 재방문 효과가 검증됐다는 뜻이 아니다. 검증 범위는 NATIVE_DESIGN_STATUS.md를 따른다.

## 2026-09-07 report-based native upgrade

ACT-01: Work/Rest/Presence/Personal goals and a chosen time window now prefill the first local rule. Save and Activate remain separate. OFFER-01: Firebase Apple authentication/goal storage and RevenueCat subscription UI/services are implemented in source. Credentials are not configured, no paid benefit is approved or gated, and the offer-ready flag stays false. Existing rules and free recovery remain available. Google sign-in, rule cloud sync, Analytics and retention experiments are not implemented in this change. See [implementation status](PAYWALL_UPGRADE_STATUS.md) and [connection setup](ACCOUNT_PURCHASE_SETUP.md).

## 2026-09-07 구역 알림·해제권 후속 구현 (D-12)

최신 요청에 따라 활성 GPS 규칙의 진입·이탈과 차단 정책 시작·종료 로컬 알림을 구현했다. 홈의 벨 버튼에서 사용자가 켜며 알림 거절은 차단 기능을 막지 않는다. 런타임의 저장된 체크포인트로 앱/확장 중복을 방지한다. iOS 알림·위치 이벤트 전달과 실제 차단은 실기기 검증 대기다. 동시에 활성인 규칙은 여전히 하나이며 실내 구분을 보장하지 않는다.

OFFER-01 해제권 화면은 **현재 세션 하나를 조기 종료하는 일회성 구매** 제안이다. 예전 15분 통행권/자동 재차단 기획을 구현한 것이 아니다. 구매 상품·가격·검증된 지급/사용 처리 없이 금액이나 가짜 구매 성공을 표시하지 않으며 현재 구매는 불가하다. 일반 종료 및 집중 중 활성 규칙 교체·삭제가 화면을 거친다. SAFE-01 긴급·오류 복구는 별도 무료 행동이다. 구독 entitlement로 소모성 해제권을 처리하지 않는다. 실제 유료 출시 전 상품 구성·결제 검증·취소/대기/중복/실패 복구와 활성화 전 조건 고지가 필요하다.

NFC에는 앱 외에 별도 물리 태그가 필요하다. 현재 별도 도구로 `https://roomdns.app/focus/<tag-id>` NDEF URL을 기록한 태그를 앱 안에서 읽는다. 앱에는 태그 쓰기 기능이 없으며 준비된 어떤 태그도 현재 NFC 규칙을 시작할 수 있다. 태그로부터의 이탈은 감지하지 않는다.

## 2026-09-07 선택형 앱 삭제 제한 (D-13)

규칙 검토 화면에 `Prevent app deletion during blocking` 옵션을 추가했다. 기존 규칙은 필드가 없어도 읽히며 기본 꺼짐이다. 실제 공유 차단 정책이 참인 경우에만 `ManagedSettingsStore.application.denyAppRemoval`을 설정하고, 정책 종료·구역 이탈·복구·오류 시 기존 `clearAllSettings`로 함께 해제한다. 다른 규칙의 옵션이 꺼져 있으면 명시적으로 nil을 적용하여 이전 설정을 남기지 않는다.

이 설정은 모든 앱의 삭제에 영향을 주며 우리 앱만을 대상으로 하지 않는다. iPhone 전체 화면 잠금·권한 철회 불가·결제 외 해제 불가를 보장하지 않는다. 개인용 Screen Time 권한은 사용자가 설정에서 철회할 수 있다. 정상 일정 종료/조건 해제와 무료 오류·긴급 복구는 유지한다. 해제권 구매는 아직 비활성이다. 실기기에서 삭제 방지와 해제를 확인하기 전 출시 완료로 표기하지 않는다.

근거: [Apple denyAppRemoval](https://developer.apple.com/documentation/managedsettings/applicationsettings/denyappremoval-swift.property), [Apple 직원 안내](https://developer.apple.com/forums/thread/729637), [개인 권한 철회 설명](https://developer.apple.com/videos/play/wwdc2022/110336/). 감독 기기용 [AppLock](https://developer.apple.com/documentation/devicemanagement/applock)은 현재 개인 앱과 다른 관리 모델이다.

## D-22 / OFFER-01 · INSIGHT-01 — 2026-09-09
Pro includes app-owned weekly intention planning and private, self-reported daily reflections with weekly counts. This is not automatically measured Screen Time, focus duration or saved time. New writes require a verified current-account Pro entitlement that has not expired. Reading, deleting and exporting existing local records remain available after expiry or sign-out; no cloud sync or backup is promised. The implemented UI is in Account & plan → Plan & reflect. Sales flags remain disabled pending pricing, real purchase validation and product review. Pass USD0.99 was approved and saved; Pro monthly/annual price is not yet confirmed. See `_workspace/PRO_IMPLEMENTATION_STATUS.md` for checks and limitations.

## Android first slice — 2026-09-22
User approved Kotlin + Jetpack Compose alongside the existing SwiftUI iOS app. Initial Android scope: explicitly started timed sessions, third-party app selection, local AccessibilityService shield, free restore, English/Korean. Session ends on service/process loss or restart. System apps excluded. Location/account/billing integration and Play release remain future work. Build/device evidence: [Android implementation](android/README.md).
