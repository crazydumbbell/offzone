# Offzone Design System

## 첫 설정 시간·Pro 진입 — 2026-09-25 (D-45)

시간 카드의 기본 아침/오후/저녁 외에 **Another time**을 두고 네이티브 시간 선택기를 펼친다. **Not sure yet**은 선택 목표의 추천 1시간을 명시해 선택 부담을 줄인다. 선택 카드와 시간 텍스트는 영어 원문·한국어 보조 현지화를 따른다. 첫 규칙 저장 뒤 준비 화면에서 `Activate my rule` 성공 시 Nook Cat이 있는 기존 `RoomPaywallView`를 바로 표시하고, `Continue with free`/닫기와 무료 `Restore access`를 유지한다. 판매 준비가 꺼져 있으면 실제 가격·7일 체험 주장이나 구매 버튼 없이 무료 사용 안내를 보여준다. 첨부 영상은 시각 참고다. iOS 시뮬레이터에서 영어·한국어 시간 선택과 Pro 화면 렌더링을 확인했으며 전체 활성화 흐름·실기기·Android 동등 흐름은 별도 확인한다.

## Nook Cat 네이티브 배치 — 2026-09-25 (현재 로컬 구현)

사용자가 [보드 F](.growth-design/mascots/2026-09-25-cute-10x10/index.html#candidate-F)를 선택했다. 원본의 버터색 장면 배경을 앱에 넣지 않고 새 RGBA 표정8·전신4를 [Nook 앱 팩](.growth-design/mascots/2026-09-25-nook-native/MASCOTS.md)으로 제작했다. iOS의 13개 `RoomSpiritState`는 작은 슬롯에서 얼굴 표정, 넓은 슬롯에서 니트 조끼가 보이는 전신을 선택한다. Android 온보딩은 첫 화면 전신과 다음 두 단계의 표정을, 홈은 준비/집중 전신을 사용한다. SUIT, 버터/차콜 화면, 공식 `off` 아이콘과 무료 복구·명시적 집중 시작을 유지한다. 알파 채널이 있다고 윤곽이 완전히 깨끗하다는 뜻은 아니므로 실제 합성 화면과 실기기·접근성 검수를 별도로 기록한다. 아래 Rue·Kiwi 절은 당시 기록이다.

## Rue v6 네이티브 적용 + 의상 컨셉 — 2026-09-24 (과거 구현)

사용자가 무릎 위 A라인 치마의 **성인 Rue v6**를 앱 마스코트로 고정하고 Kiwi 전체 교체를 승인. [새 표정8/전신4](.growth-design/mascots/2026-09-24-rue-statebook/index.html)를 `OffzoneRue-*` iOS catalog 및 Android `rue_*` 정적 PNG로 다운샘플하여 상태 매핑하고 기존 Kiwi 그림·영상 번들은 제거. [가공 소스/해시/제약](.growth-design/mascots/2026-09-24-rue-native/manifest.json) · [새 전신 옷 3종 비교](.growth-design/mascots/2026-09-24-rue-wardrobe/index.html). SUIT·버터/차콜 팔레트와 공식 `off` 아이콘, 무료 복구/명시적 집중 시작 유지. 새 의상은 아직 **전신 그림 시안일 뿐 앱 장착·획득·판매 기능이 아님**. 낮은 알파 배경 워시를 감쇠했으나 clean alpha 인증과 사용권/실기기 검증이 없어 출시는 별도 게이트. 아래 Kiwi 절은 **2026-09-21 당시 구현의 역사 기록**; 현재 앱 상태로 읽지 않는다.

## Rue v6 선택 방향 및 새 상태 레퍼런스 — 2026-09-24 (과거 설계 단계)

[표정8·전신4 신규 생성 레퍼런스](.growth-design/mascots/2026-09-24-rue-statebook/index.html) 및 [당시 적용 설계](.growth-design/mascots/2026-09-24-rue-statebook/IMPLEMENTATION_PLAN.md)는 D-43보다 앞선 방향 연구. 제품용 clean alpha/원본성·축소·접근성 게이트는 여전히 유효하다.

## 키위새로 변경 — 2026-09-21 과거 구현

사용자가 고양이 방향을 취소하고 첨부한 키위새로 재디자인 지시. 둥근 갈색 몸통 #CF944C, 긴 노란 부리/발 #E4B431, 차콜 선 #30322F을 살린 네이티브 SVG 8종을 적용했다. 당시 자산 `OffzoneKiwi-*.imageset`, 상태 enum `OffzoneKiwiExpression`. 고양이 원본은 `design/character/cat-archive/`에 보존하고 앱 리소스에서 제거했다. SUIT와 버터색/차콜 화면은 유지. 한영 접근성 인사도 키위새로 변경. 첫 화면/로딩/집중/확인·완료/복구/오류/대기 경로와 Reduce Motion 그대로.

비교 이미지 `output/kiwi-suit-2026-09-21/kiwi-states.svg.png`. 이전 고양이·깨비 기록보다 이 결정이 우선한다. 출시/스토어 업로드 없음.

## SUIT + 버터색 고양이 — 2026-09-21 과거 결정

사용자가 SUIT를 선택하고 버터색 배경·손그림 고양이 레퍼런스를 추가했다. 깨비/새싹 마스코트를 Offzone용 얇은 잉크 선 고양이 8종으로 교체했다. 이전 벡터는 `design/character/leaf-archive/`, 당시 자산은 `RoomDNS/Assets.xcassets/OffzoneCat-*.imageset`에 있다. 인사·로딩·확인/완료·복구·주의·오류·집중·대기 상태 매핑과 Reduce Motion을 유지한다.

앱 소유 SwiftUI 화면의 텍스트는 SUIT Regular/Medium/SemiBold/Bold 정적 TTF(총4개), 동적 글자 크기 지원. 본문/버튼16–17pt·보조 최소14pt 기준. 폰트/OFL은 `RoomDNS/Fonts/`, Info.plist UIAppFonts 및 앱 리소스 등록. 시스템 권한 대화상자·지도 라벨·Apple 로그인 버튼·확장 화면은 플랫폼 서체를 유지한다.

색은 버터색 #F7F0C7, 크림/흰 카드, 차콜 #191B19. 홈 상태 카드는 실제 상태를 유지하며 좌측 정보·작은 고양이·명시적 Start focus를 배치. 경고/복구는 구별되는 밝은 면 유지. 장소 MapKit 검색/핀/지도 중심/현재 위치 컨트롤 정돈. 기존 규칙 행은 편집·일시정지·삭제·미적용 변경 기능 때문에 유지했다. 정적 비교안의 예시 데이터나 상태를 런타임에 넣지 않았다.

검증: `_workspace/cat-suit-tests.log` 25실행/24통과/StoreKit1skip/0실패. 실제 SUIT4종 로딩·기본크기14pt이상·고양이8종 로딩/매핑 포함. 자산·한영 리소스·plist 검사 통과. 실기기 최대 글자 크기·VoiceOver·Screen Time 확인 및 스토어 업로드는 별도 미완료.

## Offzone 리디자인 — 2026-09-21 사용자 정정

이전의 원본 그대로 복사 지시는 해석 오류였다. 최신 사용자 지시에 따라 둥근 실루엣과 8종의 표정 변형을 바탕으로 **Offzone 전용 파생 디자인**을 구현했다. 원본 HTML/SOURCE-README는 참고 자료로 보존한다.

- 흰 블롭을 종이색 `#FAF9F3`로, 검정 굵은 선을 딥그린 `#294F40`의 2.5–4pt 선으로 조정.
- 주황 뿔·송곳니 대신 세이지 `#8EAA8D` 새싹, 옅은 잎색 볼터치. 곡선 실루엣 정돈.
- 완료는 웃는 눈과 작은 체크, 준비는 온화한 응원, 오류는 눈물 없는 걱정, 집중은 Z 장식 없는 감은 눈. 하트·방망이·별 눈 제거.
- 첫 화면 캐릭터 영역 230→186pt, 파란 아치를 세이지 28pt 둥근 면으로 변경. 다른 앱 화면의 기존 팔레트·서체·off 로고 유지.
- 로딩 둥실 ±3pt/2800ms, 완료 점프 6pt로 절제. Reduce Motion/VoiceOver/비활성 중단 유지.

이 절이 아래 원본 그대로 적용 기록보다 우선한다. 검증 결과는 MEMORY 최신 항목 참조.

## 깨비 적용 — 2026-09-21 과거 결정

사용자 지시로 기존 Roomie 그림을 Jumggabe 깨비 원본 SVG 8종으로 교체했다. 첫 화면·로딩·완료를 포함한 상태 매핑과 모션은 [캐릭터 적용 기록](design/character/README.md)을 따른다. 아래 Roomie 그림·구 모션 설명보다 이 기록이 우선하며, 앱 팔레트·서체·공식 off 로고는 유지한다. Xcode 약관 미동의로 변경 후 빌드·실제 화면 확인은 대기 중이다.

현재 구현 체크포인트: [R1 실행 기록](R1_IMPLEMENTATION_STATUS.md). 영어 원문·상시 무료 복구·저장과 적용 분리가 반영됐다. 아래 기존 한국어 문안·10초 대기·통행권 예시는 현재 동작이나 확정 상품의 근거가 아니다.

## Official logo palette — 2026-09-08 최신 구현

사용자가 제공한 `off` 워드마크를 공식 로고와 AppIcon으로 확정했다. 최신 지시에 따라 앱과 Roomie는 기존 Roomie v2 색상을 유지하고 로고를 앱 팔레트에 맞춘다. 공식 로고는 종이색 `#FAF9F3` 배경과 딥그린 `#223E38` 워드마크이며, 원본 라임 파일은 `assets/brand/offzone-logo-source-lime.png`에 보존한다. 앱용 1024px 자산은 `RoomDNS/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`다.

## Native Roomie v2 — 2026-09-07

사용자가 선택한 웹 개선안을 실제 SwiftUI 화면에 반영했다. 이 절과 [ContentView.swift](RoomDNS/App/ContentView.swift)가 아래 이전 Editorial revision의 잉크 격자·전체 카드·색상 규칙보다 우선한다. 웹 시안의 축약 흐름이나 예시 데이터를 네이티브 기능으로 옮긴 것은 아니다.

- 배경 `#FAF9F3`, 본문 `#223E38`, 보조 글자 `#596C61`, 구분선 `#DDDFD5`, 파란 면 `#E2EDF0`, 주요 버튼 `#294F40`, Roomie 문턱 `#2E61BA`를 사용한다. 흰 몸과 기존 벡터 실루엣은 유지한다.
- Home은 `Your space` 아래 실제 규칙 이름·상태 제목·중앙 Roomie·필요한 시간을 세로로 배치한다. 파란 상태 면은 18pt 모서리와 오른쪽 아래 76pt 곡선을 사용한다. 오류는 버터색, 접근 복구는 민트색으로 보조한다. 규칙 목록의 큰 외곽 카드를 제거하고 얇은 구분선과 작은 아이콘 면을 사용한다.
- Welcome은 아치형 파란 Roomie 영역, 설정 화면은 오른쪽 위 104×94pt 캐릭터와 카드 밖 제목, 복구 sheet는 중립 자세와 실제 재개 조건을 사용한다. 입력·지도·선택 같은 기능 구획은 필요한 면을 유지한다.
- **사용자 추가 지시: “폰트는 기본디자인꺼 써.”** 기존 시스템 서체 역할을 유지한다. 제목은 system serif bold, 본문·행동은 system rounded, 시간·브랜드는 system monospaced를 사용하고 일부 일반 메타데이터는 기본 system font를 따른다. 웹의 Georgia나 새 폰트 패키지를 가져오지 않는다. 의미 기반 글자 크기와 접근성 크기에서의 줄바꿈을 유지한다.
- `Restore access`는 모든 앱 소유 화면 상단에 남는다. Save/Activate/Apply changes/Resume, 삭제 확인, GPS·NFC·권한 오류 흐름은 유지한다. 홈 상태와 시간은 AppModel의 실제 저장·적용 상태를 읽으며 웹 시안의 성공 예시를 표시하지 않는다.

이 기록은 소스 반영 범위다. 시뮬레이터·실기기 검증 결과는 실행 기록에서 별도로 관리하며, 시각 선호나 재방문율 개선이 검증됐다는 뜻은 아니다.

## Status

- Direction: Editorial Threshold + Roomie
- Name: Editorial Threshold
- Register: product
- Platform: iPhone, SwiftUI
- Source of truth: PRODUCT.md; strategy decisions in ORCHESTRATOR.md
- Strategy revision: G1
- Last reviewed: 2026-09-08 (기존 Roomie v2 유지·공식 로고 색상 동기화)

## Strategy Alignment

영어를 고객 문안 원본으로 하고 글로벌 디지털 자기관리 고객을 설계한다. 업무·휴식·함께하는 시간·개인 목표를 동등하게 다루며 학생용 시각 언어로 한정하지 않는다. 새 고객 여정·화면·영어 문안은 [UX_STRATEGY_REPORT.ko.md](UX_STRATEGY_REPORT.ko.md)를 따른다. 공통 우선순위는 [ORCHESTRATOR.md](ORCHESTRATOR.md)에서 조율한다.

아래 한국어 와이어프레임·유료 통행권 화면은 기존 설계 참고이며 새 출시 문안·확정 과금안이 아니다. Pro와 통행권 선택은 `OFFER-01` 미확정 사항이다. 현재 메인 앱 dark 고정과 향후 시스템 테마 대응 제안도 구분한다. 앱 아이콘은 2026-09-08 확정된 공식 `off` 워드마크를 사용하며 Roomie는 앱 내부 안내·상태 역할로 유지한다.

## Copy restraint — 2026-09-07

사용자 지시에 따라 홈의 반복 안내·성공 문단을 제거한다. 짧은 상태와 필요한 시간만 표시하고, 규칙은 이름·시간·한 줄 상태·행동으로 구성한다. 실패 안내는 문제가 있을 때 표시한다. NFC 준비 방법은 접힌 상세 안내에 둔다.

## Editorial visual revision — 2026-09-07

사용자 레퍼런스에서 얇은 잉크 그리드, 밝은 종이 배경, 절제된 파스텔 셀, 강한 타이포 대비를 가져온다. 기존 Roomie의 형태·상태·모션과 실제 화면 흐름은 유지한다. 아래 과거 다크 고정·단일 서체 설명과 충돌하면 이 절과 현재 SwiftUI 구현이 우선한다.

- `roomCanvas #F2F2ED`, `roomPaper #FDFDF9`, `roomInk #111111`을 기본으로 쓴다.
- 보조 면은 `roomBlue #E5F1F6`, `roomMint #EAF3DC`, `roomBlush #F8E9EE`, `roomButter #F8F2DA` 네 색만 사용한다.
- 1pt 잉크 선과 10–16pt radius로 화면 구획을 만든다. 그림자와 겹친 카드 장식은 쓰지 않는다.
- 브랜드·화면 제목은 system default black, 상태·에디토리얼 제목은 system serif bold, Roomie 문맥·행동은 system rounded, 시간·단계·개수는 system monospaced를 쓴다. 모두 의미 기반 Dynamic Type 크기를 사용한다.
- 밝은 화면에서도 Roomie는 흰 몸, 잉크 윤곽·얼굴, 코발트 문턱을 유지한다.
- 달력·체크·통계·하단 탭은 현재 기능이 아니므로 레퍼런스에서 가져오지 않는다.

## Design Intent

Offzone은 통제 앱처럼 보이지 않는다. 사용자가 어떤 공간의 문턱을 넘는 순간, 산만했던 주의가 한 방향으로 정렬되는 경험을 만든다.

물리적 장면은 창가 책상, 저녁의 거실, 함께 시간을 보내는 공간이다. 얇은 선들이 흩어져 있다가 문턱을 지나며 가지런해진다. 밤에는 같은 화면이 차분한 휴식 공간처럼 낮아진다. 화면은 현재 규칙의 상태를 읽기 쉽게 전달한다.

첫 실행은 밝은 종이색 화면과 얇은 잉크 구획으로 고정한다. 통통한 방 정령 `룸이`가 낯선 권한 설정 전에 앱의 역할을 친근하게 소개하며, 이후에는 한 화면에 질문과 행동을 하나씩만 둔다. 문턱은 룸이 발 아래의 짧은 코발트 선으로 남아 제품 상태를 설명한다.

캐릭터의 형태, 레퍼런스, 상태 계약, 화면별 모션은 [MOTION.md](MOTION.md)를 따른다.

## Reference Method

Pinterest는 완성 화면의 정답이 아니라 패턴 탐색용 무드보드로 사용한다. 핀의 색, 배치, 카피를 그대로 가져오지 않는다. 각 자료에서 한 가지 원리만 추출하고, Offzone의 고유 문법으로 다시 만든다.

### Pinterest board notes

| Reference | Extract | Reject |
|---|---|---|
| [Mindful Dashboard](https://in.pinterest.com/pin/882494489487960589/) | 큰 상태 오브젝트와 넓은 여백 | 지나치게 낮은 대비, 비어 있는 원형 대시보드 |
| [Add Location UI](https://in.pinterest.com/pin/382665299609759094/) | 검색, 지도 확인, 단일 확인 행동의 순서 | 택시 앱처럼 보이는 경로 UI |
| [Maps in UI Design](https://www.pinterest.com/pin/498844096216481529/) | 지도와 하단 정보 영역의 역할 분리 | 오래된 dark map 스타일, 홈 화면의 상시 지도 |
| [Screen Time Blocker](https://in.pinterest.com/pin/437693657557955391/) | 규칙과 통계에 같은 기하 언어 사용 | 보라색 생산성 대시보드, 다중 차트, 캐릭터화된 도형 |
| [Zeng Onboarding](https://in.pinterest.com/pin/579205202111183880/) | 한 화면에 질문 하나와 명확한 다음 행동 | 유아적인 캐릭터, 장식 곡선, 과도한 단계감 |
| [Focus Timer](https://in.pinterest.com/pin/618541330111264275/) | 핵심 시간의 강한 시각 위계 | 흔한 원형 타이머, 산호색 물결 장식 |
| [Clearful Paywall](https://in.pinterest.com/pin/613615518019259299/) | 구매 이후의 시간 흐름을 먼저 설명 | 구독 카드 경쟁, 인기 배지, 월 환산액 강조 |
| [Geometric Arches](https://www.pinterest.com/pin/1142295892983303245/) | 반복 선과 아치로 통과감을 만드는 방식 | 원본 아치 형태와 구성을 그대로 재현 |

Pinterest 자료는 제품에 포함하거나 재배포하지 않는다. 최종 그래픽은 아래의 Roomie 문법으로 새로 그린다.

## Chosen Direction: Editorial Threshold + Roomie

### Why this direction

- 장소 기반 동작을 지도 핀보다 더 감성적으로 전달한다.
- 차단을 감옥이 아니라 사용자가 선택한 경계로 표현한다.
- 통통한 캐릭터가 낯선 권한 설정의 긴장을 낮추고 상태를 짧게 연기한다.
- 홈, shield, NFC, 통행권을 하나의 시각 문법으로 연결할 수 있다.
- SwiftUI Canvas와 Shape만으로 가볍고 선명하게 구현할 수 있다.
- 텍스트 없이도 상태 변화의 방향을 보조할 수 있다.

### Visual style frame

![Quiet Threshold style frame](assets/quiet-threshold-style-frame.png)

이 이미지는 방향 확인용으로 생성한 원본 스타일 프레임이다. 앱에 그대로 삽입하지 않는다. 실제 제품에서는 종이 질감과 입체 음영을 줄이고, 선과 면을 SwiftUI로 다시 그려 크기, 모션, 색 대비를 통제한다.

최신 캐릭터와 화면 배치 기준은 [Roomie keyframes](assets/motion/room-spirit-keyframes-v1.png), [screen storyboard](assets/motion/room-spirit-screen-storyboard-v1.png)다.

### Original image prompt

```text
Create a refined vertical abstract composition expressing a threshold between distraction and focus. Use a warm mineral off-white matte surface, one centered architectural opening made from nested rounded rectangular lines, sparse graphite traces that become evenly aligned as they cross the opening, and one restrained cobalt slit. Keep strong negative space and a quiet architectural mood. Do not use text, logos, phones, people, locks, shields, timer circles, neon, glass, or direct imitation of existing artwork.
```

## Roomie + Threshold Grammar

문이나 자물쇠를 직접 그리지 않는다. 네 가지 원소만 사용한다.

1. `Body`: 넓고 비대칭인 흰 덩어리
2. `Face`: 가까이 모인 눈 두 획과 입 한 획
3. `Gesture`: 안내할 때만 나타나는 작은 손
4. `Threshold`: 발 아래의 짧은 코발트 선

### Meaning

- 몸이 편안히 호흡한다: 장소 밖 또는 선택 가능
- 몸을 조금 세우고 본다: 규칙이 준비됨
- 몸이 넓고 낮게 가라앉는다: 집중 규칙 작동 중
- 코발트 문턱이 줄어든다: 15분 통행권 남은 시간
- 몸이 기울고 문턱이 warning 색으로 바뀐다: 권한 또는 위치 확인 필요

표정과 문턱 길이는 사용자의 앱 종류나 개인 정보를 나타내지 않는다.

## Color Strategy

`Restrained` 전략을 사용한다. 따뜻한 중성색과 잉크색이 화면 대부분을 차지하고, 코발트는 주요 행동과 실제 상태 변화에만 쓴다.

OKLCH를 디자인 원본으로 관리하고 Xcode Asset Catalog에는 검증된 sRGB의 Light, Dark, Increased Contrast 변형을 등록한다. 런타임 색 변환 패키지는 만들지 않는다.

### Core tokens

| Token | Light OKLCH | Light sRGB | Dark OKLCH | Dark sRGB | Role |
|---|---|---|---|---|---|
| `canvas` | `0.965 0.010 85` | `#F6F3EC` | `0.180 0.015 255` | `#0D1218` | 전체 배경 |
| `surface` | `0.985 0.006 85` | `#FCFAF6` | `0.220 0.018 255` | `#151B23` | 입력과 목록 표면 |
| `surfaceRaised` | `0.945 0.012 85` | `#F0ECE4` | `0.270 0.020 255` | `#202730` | 구분이 필요한 단일 패널 |
| `ink` | `0.245 0.025 255` | `#18212C` | `0.930 0.008 85` | `#EAE7E2` | 기본 텍스트 |
| `inkSecondary` | `0.460 0.025 255` | `#4F5966` | `0.740 0.015 255` | `#A5ABB4` | 보조 텍스트 |
| `inkTertiary` | `0.540 0.020 255` | `#676F7A` | `0.630 0.018 255` | `#828A94` | 비핵심 메타데이터 |
| `separator` | `0.860 0.015 85` | `#D5D0C6` | `0.340 0.020 255` | `#313942` | 구분선 |
| `accent` | `0.520 0.165 260` | `#2964C6` | `0.720 0.120 255` | `#6FA7EE` | 주요 행동과 선택 |
| `accentPressed` | `0.440 0.145 260` | `#1B4EA1` | `0.640 0.130 255` | `#528ED9` | 눌림 |
| `accentSoft` | `0.910 0.035 260` | `#D4E2F9` | `0.310 0.065 255` | `#183150` | 열린 틈과 선택 배경 |
| `warning` | `0.540 0.110 70` | `#976213` | `0.780 0.120 75` | `#E4AC59` | 권한 확인과 안전 해제 |
| `danger` | `0.550 0.170 30` | `#C13E2E` | `0.720 0.140 30` | `#EF806F` | 실제 오류와 삭제 |
| `success` | `0.520 0.110 155` | `#267B4C` | `0.700 0.100 155` | `#69B183` | 테스트와 구매 완료 |

### Color rules

- 코발트는 전체 화면의 10% 이하를 목표로 한다.
- `warning`은 권한 문제와 무료 안전 해제에만 사용한다.
- `danger`는 구매 유도에 사용하지 않는다.
- 상태는 색, 텍스트, 형태를 함께 바꾼다.
- 의미 있는 작은 텍스트는 배경과 4.5:1 이상 대비를 유지한다.
- 순백과 순흑을 사용하지 않는다.
- Light, Dark, Increased Contrast를 각각 검증한다.

## Typography

폰트 파일을 포함하지 않는다. SwiftUI의 의미 기반 시스템 크기와 네 시스템 디자인을 역할별로 사용한다. 라틴 문자는 SF 계열과 New York 계열, 한국어는 iOS 시스템 대체 글꼴이 자연스럽게 렌더링되게 한다.

| Token | SwiftUI design | Style / weight | Role |
|---|---|---|---|
| `brand` | `.default` | `.largeTitle` / Black | 화면 이름과 브랜드 |
| `editorial` | `.serif` | `.largeTitle`–`.headline` / Bold | 가치 제안, 상태, 규칙 이름 |
| `companion` | `.rounded` | `.body`–`.headline` / Regular–Bold | Roomie 문맥, 설명, 행동 |
| `metadata` | `.monospaced` | `.caption`–`.body` / Medium–Bold | 단계, 시각, 일정, 개수 |
| `body` | `.default` | `.body` / Regular | 긴 안내와 오류 |

### Type rules

- 시간과 가격 숫자에만 `.monospacedDigit()`를 추가한다.
- Thin, Light weight를 사용하지 않는다.
- 본문, 가격, 상태에 `minimumScaleFactor`를 사용하지 않는다.
- 의미 있는 텍스트에 한 줄 제한을 두지 않는다.
- 한 화면 안에서 각 역할을 무작위로 바꾸지 않는다.
- 별도 폰트 파일이나 폰트 패키지를 추가하지 않는다.
- 구매 가격은 최소 `.title2`, 결제 조건은 최소 `.footnote`다.

## Spacing and Shape

### Spacing

| Token | Value | Role |
|---|---:|---|
| `space1` | 4pt | 아이콘 내부 보정 |
| `space2` | 8pt | 밀접한 레이블 |
| `space3` | 12pt | 아이콘과 텍스트 |
| `space4` | 16pt | 기본 내부 여백 |
| `space5` | 20pt | iPhone 화면 좌우 여백 |
| `space6` | 24pt | 주요 블록 내부 |
| `space8` | 32pt | 섹션 사이 |
| `space12` | 48pt | 상태와 행동 영역 사이 |

- iPhone의 기본 좌우 여백은 20pt다.
- 주요 컨트롤은 safe area 안에 둔다.
- 배경과 Roomie hero만 edge-to-edge로 확장할 수 있다.
- 버튼 시각 높이는 최소 50pt, hit region은 최소 44 x 44pt다.
- 설정 행은 최소 56pt이며 Dynamic Type에 따라 늘어난다.
- 넓은 화면의 읽기 영역은 최대 560pt다.

### Radius

- `radiusControl`: 12pt
- `radiusPanel`: 20pt
- `radiusGraphic`: 28pt
- Capsule은 짧은 상태 배지와 진행 트랙에만 쓴다.
- 시스템 sheet와 navigation bar의 radius를 덮어쓰지 않는다.
- 중첩 카드와 모든 요소의 pill 처리를 금지한다.

## Information Architecture

MVP는 탭 바 없이 홈을 앱의 기둥으로 삼는다. 이름 붙인 규칙은 여러 개 저장할 수 있지만 한 번에 하나만 활성화한다. 현재 상태와 규칙 목록을 한 화면에서 함께 보여주고, 기록과 설정이 실제 출시 범위가 되기 전에는 별도 탭을 만들지 않는다.

```text
Onboarding
  → Welcome
  → Permission explanation
  → App and site selection
  → Place or NFC setup
  → Schedule
  → Review and save
  → Home

Home
  → Current status
  → Saved rule list
      → Activate rule
      → Rule detail and edit
      → New rule
  → Last 7 days
  → Settings
  → Reflection
      → Continue focus
      → 15-minute pass
          → Apple purchase sheet
          → Pass active
      → Free safety release
```

기록과 설정이 독립적인 반복 방문 목적지가 될 만큼 커질 때만 탭 구조를 다시 검토한다.

## Native Component Policy

우선 사용하는 요소:

- `NavigationStack`
- `List`와 `Form`
- `Picker`, `DatePicker`, `Toggle`
- `Button`
- `Map`
- `sheet`와 `confirmationDialog`, 맥락상 필요한 경우만 사용
- 시스템 toolbar와 navigation title

앱 전용 컴포넌트는 네 개만 만든다.

1. `RoomSpirit`: 캐릭터와 문턱 상태 그래픽 (`ThresholdField`는 전환 전 구현 이름)
2. `StatusSummary`: 상태, 장소, 종료 시각
3. `RuleSummary`: 활성 상태와 전환 행동을 포함한 저장 규칙 행
4. `PassOutcome`: 15분 동안 바뀌는 결과 요약

한 구현체뿐인 protocol, 커스텀 디자인 프레임워크, 독자적인 form control을 만들지 않는다.

## Core Screen Specifications

### 1. Welcome

User question: 이 앱이 무엇을 해주는가?

```text
┌─────────────────────────────┐
│                             │
│           Roomie            │
│          outside            │
│                             │
│ 장소가 집중을               │
│ 기억해요                     │
│                             │
│ 정한 장소와 시간에 고른       │
│ 앱과 사이트를 조용히 가려요   │
│                             │
│ [ 시작하기                 ] │
│        어떻게 작동하나요      │
└─────────────────────────────┘
```

- 주 행동: `시작하기`
- 보조 행동: 간단한 동작 설명
- 로그인은 계정 기능이 생기기 전까지 노출하지 않는다.
- 본 설정은 `Screen Time → 대상 → 장소 → 시간 → 확인`의 5단계다.
- 금지: 세 장짜리 기능 carousel, 권한 한꺼번에 요청, paywall

### 2. Place setup

User question: 어디에서 이 규칙을 사용할 것인가?

```text
┌─────────────────────────────┐
│ 장소 추가                    │
│ [ 주소나 장소 검색          ] │
│                             │
│                             │
│          Apple Map          │
│        반경 원과 핀 1개      │
│                             │
│ ─────────────────────────── │
│ 서재가 있는 집               │
│ 반경 150m                    │
│ [ 이 장소 사용             ] │
└─────────────────────────────┘
```

- 지도는 설정에서만 전체 면적을 사용한다.
- 핀과 반경을 동시에 보여준다.
- GPS가 방을 구분한다는 카피를 쓰지 않는다.
- NFC를 선택하면 별도의 `책상 태그로 시작` 흐름으로 전환한다.

### 3. Home, focused

User question: 지금 무엇이 작동하고 언제 끝나는가?

```text
┌─────────────────────────────┐
│ Offzone                 ⚙︎ │
│                             │
│           Roomie            │
│          focused            │
│                             │
│ 집중 중                      │
│ 서재                         │
│ 14:32에 다시 열림            │
│                             │
│ Instagram 외 3개 가림        │
│ 평일 09:00부터 14:32         │
│ ─────────────────────────── │
│ 오늘 지킨 시간        47분    │
│                             │
│ [ 규칙 확인                ] │
└─────────────────────────────┘
```

- 그래픽보다 상태 문장이 먼저 의미를 완성해야 한다.
- 현재 위치를 지도나 좌표로 표시하지 않는다.
- 기록은 한 줄만 보여주고 차트 대시보드로 확장하지 않는다.

### 4. Reflection, six seconds

User question: 정말 지금 열고 싶은가?

```text
┌─────────────────────────────┐
│                             │
│      가라앉은 Roomie  6      │
│                             │
│ 47분 동안 집중을 지켰어요     │
│ 잠시 멈추고 선택해 주세요     │
│                             │
│ [ 계속 집중하기            ] │
│                             │
│ 6초 후 통행권 선택이 열립니다 │
└─────────────────────────────┘
```

- `계속 집중하기`는 즉시 동작한다.
- 통행권 선택은 사전 동의한 사용자에게만 6초 뒤 활성화한다.
- countdown은 실제 6초 숙고를 표시하며 구매 마감처럼 보이게 하지 않는다.
- VoiceOver는 매초 알리지 않고 시작과 활성화 시점만 알린다.

### 5. Fifteen-minute pass

User question: 무엇을 얼마에, 언제까지 여는가?

```text
┌─────────────────────────────┐
│ ‹ 집중으로 돌아가기          │
│                             │
│    Roomie + 코발트 문턱      │
│                             │
│ 15분만 문을 열까요?          │
│                             │
│ Instagram 외 1개             │
│ 14:32에 자동으로 다시 가림    │
│                             │
│ 1회 결제, 자동 갱신 없음      │
│ 실제 StoreKit 가격           │
│                             │
│ [ {price}로 15분 열기       ] │
│ [ 계속 집중하기            ] │
│                             │
│ 위치 오류 또는 긴급 상황인가요?│
│ 무료 안전 해제               │
└─────────────────────────────┘
```

#### Hierarchy

1. 결과: 15분, 열리는 대상, 재차단 시각
2. 실제 가격과 일회성 조건
3. 구매 행동
4. 비구매 행동
5. 무료 안전 해제

#### Rules

- 기능 bullet 목록과 social proof를 넣지 않는다.
- 구독 카드, 할인 배지, 가짜 정가를 넣지 않는다.
- 가격은 StoreKit에서 불러온다.
- 상품 로딩 실패 시 구매 영역을 제거하고 다른 두 행동을 유지한다.
- Apple 구매 sheet가 표시되기 전 버튼을 loading 상태로 바꿔 중복 탭을 막는다.
- 구매 취소는 원래 화면으로 조용히 돌아온다.

### 6. Pass active

User question: 언제 다시 닫히는가?

```text
┌─────────────────────────────┐
│ Offzone                 ⚙︎ │
│                             │
│           Roomie            │
│      cobalt aperture        │
│                             │
│ 잠시 열림                    │
│ 08:42 남음                   │
│ 14:32에 다시 가림            │
│                             │
│ [ 지금 다시 집중하기       ] │
└─────────────────────────────┘
```

- 남은 시간은 실제 종료 시각에서 계산한다.
- 사용자가 원하면 남은 통행권을 포기하고 즉시 다시 차단할 수 있다.
- 활성 통행권 중 추가 구매 UI를 표시하지 않는다.

### 7. Free safety release

User question: 결제 없이 현재 규칙을 끝내야 하는가?

```text
┌─────────────────────────────┐
│ 무료 안전 해제               │
│                             │
│ 위치가 잘못 감지됐거나        │
│ 급히 접근해야 할 때 사용하세요 │
│                             │
│ 해제하면 현재 집중 규칙이 끝나요│
│ 기록 손실이나 비용은 없어요    │
│                             │
│ [ 10초 뒤 무료로 해제       ] │
│ [ 취소                     ] │
└─────────────────────────────┘
```

- 커스텀 제스처가 유일한 실행 방법이면 안 된다.
- 모든 입력 방식에서 같은 10초 숙고 뒤 표준 activation 경로를 제공한다.
- 네트워크, RevenueCat, 상품 로딩과 무관하게 동작한다.

## Home State Character

Roomie는 가용 폭에서 좌우 40pt를 뺀 값과 280pt 중 작은 폭을 사용한다. 일반 상태 높이는 200pt에서 240pt다. 그래픽은 VoiceOver에서 숨기고 같은 의미를 `StatusSummary`가 전달한다.

| State | Geometry | Supporting copy |
|---|---|---|
| `outside` | 원래 높이, 드문 호흡과 blink | `장소 밖`, `서재에 들어가면 준비돼요` |
| `armed` | 몸을 2% 세우고 다음 상태를 봄 | `준비됨`, `서재 안, 오전 9시에 시작` |
| `focused` | 몸이 넓고 낮게 가라앉고 정지 | `집중 중`, `14:32에 다시 열림` |
| `passActive` | 중립 표정, 코발트 선이 시간에 따라 감소 | `잠시 열림`, `8분 42초 남음` |
| `safetyReleasePending` | 정적 자세, hold track이 우선 | `안전 해제 준비 중`, `7초 남음` |
| `needsAction` | 몸이 2–3° 기울고 문턱이 warning 색 | `확인이 필요해요`, 해결 행동 |

## Motion

상세 기준은 [MOTION.md](MOTION.md)를 따른다. 캐릭터에는 드문 idle을 허용하지만, 한 화면에서 스스로 움직이는 초점은 룸이 하나뿐이다. 앱을 열 때마다 긴 장식 애니메이션을 재생하지 않는다.

| Transition | Duration | Behavior |
|---|---:|---|
| button press | system | native press state |
| `outside → armed` | 220ms | 몸이 2% 세워지고 시선 이동 |
| `armed → focused` | 240ms | 몸이 넓고 낮게 가라앉음 |
| `focused → passActive` | 180ms | 몸이 조금 열리고 코발트 선 갱신 |
| `needsAction` | 160ms | opacity 교차 전환 |
| navigation and sheet | system | custom choreography 없음 |

- 곡선은 ease-out-quint 계열을 사용한다.
- UI에는 spring, bounce, elastic, parallax를 쓰지 않는다. 캐릭터의 완료 동작만 keyframe으로 4% 이하의 짧은 overshoot를 허용한다.
- 상시 떠다니는 입자는 쓰지 않는다. 캐릭터 idle은 foreground에서만 재생하고 집중 중에는 멈춘다.
- 애니메이션 중에도 입력을 막지 않는다.
- 상태 적용 성공 때 가벼운 haptic을 한 번만 제공한다.

### Reduce Motion

- 위치 이동, 크기 변화, 닫힘 변형을 제거한다.
- 100ms에서 120ms 교차 페이드 또는 즉시 교체를 사용한다.
- Roomie는 최종 정적 자세만 보여준다.
- 시간 숫자와 상태 텍스트는 계속 갱신한다.
- 모션 설정이 기능 실행 시간을 바꾸지 않는다.

## Accessibility

### Dynamic Type

- 모든 글자는 의미 기반 text style을 사용한다.
- 접근성 크기에서는 가로 행동을 세로 전체 폭으로 쌓는다.
- `accessibility3`부터 Roomie를 96pt에서 120pt로 줄이거나 숨긴다.
- 가격, 장소명, 종료 시각이 줄바꿈될 수 있게 한다.
- 고정 높이 본문과 한 줄 가격을 금지한다.
- Large, XXXL, AX1, AX3, AX5에서 검사한다.

### VoiceOver order

1. 화면 제목
2. 현재 상태
3. 적용 장소
4. 종료 또는 재차단 시각
5. 주요 행동
6. 보조 행동
7. 안전과 지원 행동

`StatusSummary`는 하나의 요소로 묶는다.

```text
Label: 집중 중
Value: 서재, 오후 2시 32분에 종료
```

- Roomie는 `accessibilityHidden(true)`로 처리한다.
- 남은 시간을 매초 announcement 하지 않는다.
- 구매 버튼의 보이는 레이블과 접근성 레이블에 실제 가격을 포함한다.
- 위치 상태 변화가 VoiceOver 포커스를 강제로 옮기지 않는다.
- 커스텀 제스처 외에 표준 버튼 경로를 항상 제공한다.

## Shield Design

Shield는 판매 화면이 아니다. 중립적으로 현재 약속만 설명한다.

```text
[정적인 닫힌 Threshold mark]

지금은 서재 집중 시간입니다
14:32에 끝나요

[ 지금은 닫기 ]
```

- 가격, IAP, 할인, 통행권 홍보를 넣지 않는다.
- 앱 아이콘과 대상 이름은 시스템이 허용하는 방식으로만 렌더링한다.
- 버튼 동작은 ShieldAction 공개 API 스파이크 결과에 맞춘다.
- Shield에서 Offzone 메인 앱이 직접 열린다고 약속하지 않는다.

## Error and Empty States

### Permission denied

- 제목: `Screen Time 권한이 필요해요`
- 설명: 막을 대상을 사용자가 직접 고르기 위해 필요하다고 설명한다.
- 주 행동: `설정에서 권한 확인`
- 보조 행동: `나중에`
- 남은 shield가 있다면 먼저 정리한다.

### Location unavailable

- 제목: `지금은 장소를 확인할 수 없어요`
- 설명: 현재 규칙은 자동으로 시작되지 않았음을 명시한다.
- 주 행동: `위치 설정 확인`
- 보조 행동: `NFC로 시작`
- 위치 오류를 통행권 구매로 해결하게 하지 않는다.

### Offering unavailable

- 제목: `통행권을 불러오지 못했어요`
- 설명: 결제되지 않았고 집중은 그대로 유지된다고 설명한다.
- 주 행동: `다시 시도`
- 보조 행동: `계속 집중`
- 무료 안전 해제는 그대로 표시한다.

### Purchase pending

- 제목: `구매 승인 대기 중`
- 설명: 승인 전에는 통행권이 시작되지 않는다고 설명한다.
- 중복 구매 버튼을 숨긴다.
- 현재 제한을 임의로 해제하지 않는다.

## Content Rules

- 보조 설명은 개인정보, 안전, 오류 복구에만 사용한다.
- 버튼은 다음 행동을 그대로 이름 붙인다.
- 한 문장은 한 가지 사실만 전달한다.
- 상태 카피는 현재형을 사용한다.
- 종료와 재차단은 절대 시각을 함께 보여준다.
- `잠금`, `실패`, `중독`, `벌금`, `탈출`을 기본 어휘로 쓰지 않는다.
- `가림`, `집중`, `잠시 열림`, `다시 닫힘`, `확인 필요`를 사용한다.
- 근거 없는 `과학적`, `검증된`, `최고`, `대부분의 사용자`를 쓰지 않는다.
- 버튼은 결과를 말한다. `확인`보다 `이 장소 사용`, `15분 열기`가 낫다.

## Validation Matrix

### Visual

- 375 x 667pt 소형 iPhone
- 430 x 932pt 대형 iPhone
- Light와 Dark
- Increased Contrast
- Differentiate Without Color
- Reduce Transparency
- Reduce Motion

### Accessibility

- VoiceOver와 Screen Curtain으로 온보딩 완료
- VoiceOver로 규칙 생성, 구매 취소, 무료 안전 해제 완료
- AX5에서 가격, 종료 시각, 구매와 안전 행동이 잘리지 않음
- 모든 주요 hit region 44 x 44pt 이상
- Accessibility Inspector 누락 label 0건

### Design review gates

- 홈을 3초 안에 보고 상태, 장소, 종료 시각을 말할 수 있다.
- 지도는 장소 설정 외 화면에 나타나지 않는다.
- paywall을 보고 상품 유형, 가격, 열리는 시간, 재차단 시각을 설명할 수 있다.
- 색을 제거해도 모든 상태가 구분된다.
- Pinterest 핀과 나란히 놓았을 때 동일 화면의 변형처럼 보이지 않는다.
- Shield와 paywall의 역할이 섞이지 않는다.

## Implementation Order

1. 색과 시스템 text style Asset Catalog 정의
2. 정적인 `RoomSpirit` 여섯 상태
3. `StatusSummary`와 focused home
4. Place setup과 60초 test
5. Reflection과 무료 안전 해제
6. PassOutcome과 RevenueCat paywall 상태
7. 상태 전환 motion
8. 영어 우선·한국어 보조, Dark, accessibility 검증

먼저 정적 상태와 접근성을 통과시킨 뒤 모션을 추가한다.

## Official References

- [Apple Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility/)
- [Apple Color](https://developer.apple.com/design/human-interface-guidelines/color)
- [Apple Typography](https://developer.apple.com/design/human-interface-guidelines/typography)
- [Apple Layout](https://developer.apple.com/design/human-interface-guidelines/layout)
- [Apple Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons)
- [Apple Motion](https://developer.apple.com/design/human-interface-guidelines/motion)
- [Apple VoiceOver](https://developer.apple.com/design/human-interface-guidelines/voiceover)
- [SwiftUI accessibilityReduceMotion](https://developer.apple.com/documentation/swiftui/environmentvalues/accessibilityreducemotion)
- [Apple SF Symbols](https://developer.apple.com/design/human-interface-guidelines/sf-symbols)
