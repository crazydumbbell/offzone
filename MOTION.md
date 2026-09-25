# Offzone Character & Motion System

## Rue v6 정지 그림 + 기존 반응 계산 — 2026-09-24 (현재 앱 코드)

iOS `RoomSpirit`가 새 [Rue 상반신8/전신4](.growth-design/mascots/2026-09-24-rue-statebook/index.html) 정지 PNG를 사용하며 기존 `RoomSpiritMotion.sample` 미세 호흡·`RoomSpiritReaction.sample` 단발 이동/회전 변환, Reduce Motion·가시성 일시정지를 그대로 유지. Kiwi HEVC MOV/포스터 경로는 코드·Xcode Resources에서 제거; Android는 `RueView`의 정적 이미지로 Kiwi animated WebP 경로를 대체. 이것은 **생성된 Rue 영상이나 리깅된 옷입히기 모션이 아니다**. 새 의상3종은 전신 concept PNG라 반응/표정 상태와 동기화되지 않으며 실제 장착 불가. 투명 컷아웃·작은 슬롯/권리·접근성·기기/배터리 QA와 별도 모션 제작을 출시 전 검토. 아래 과거 Kiwi 영상 기록은 역사적 자산이다.

## Rue v6 상태 정지 그림 방향 — 2026-09-24 (과거 설계 단계)

[신규 표정8/전신4](.growth-design/mascots/2026-09-24-rue-statebook/index.html) 상태 시트는 v6 앵커에서 생성했다. D-42 당시에는 앱 미적용이었으나 D-43으로 정지 리소스 교체를 진행했다.

## 키위새로 변경 — 2026-09-21 과거 구현

사용자가 고양이 방향을 취소하고 첨부한 키위새로 재디자인 지시. 둥근 갈색 몸통 #CF944C, 긴 노란 부리/발 #E4B431, 차콜 선 #30322F을 살린 네이티브 SVG 8종을 적용했다. 현재 자산 `OffzoneKiwi-*.imageset`, 상태 enum `OffzoneKiwiExpression`. 고양이 원본은 `design/character/cat-archive/`에 보존하고 앱 리소스에서 제거했다. SUIT와 버터색/차콜 화면은 유지. 한영 접근성 인사도 키위새로 변경. 첫 화면/로딩/집중/확인·완료/복구/오류/대기 경로와 Reduce Motion 그대로.

비교 이미지 `output/kiwi-suit-2026-09-21/kiwi-states.svg.png`. 이전 고양이·깨비 기록보다 이 결정이 우선한다. 출시/스토어 업로드 없음.

## SUIT + 버터색 고양이 — 2026-09-21 최신 사용자 결정

사용자가 SUIT를 선택하고 버터색 배경·손그림 고양이 레퍼런스를 추가했다. 깨비/새싹 마스코트를 Offzone용 얇은 잉크 선 고양이 8종으로 교체했다. 이전 벡터는 `design/character/leaf-archive/`, 현재 자산은 `RoomDNS/Assets.xcassets/OffzoneCat-*.imageset`에 있다. 인사·로딩·확인/완료·복구·주의·오류·집중·대기 상태 매핑과 Reduce Motion을 유지한다.

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

## 깨비 적용 — 2026-09-21 최신

사용자 지시로 기존 Roomie 그림을 Jumggabe 깨비 원본 SVG 8종으로 교체했다. 첫 화면·로딩·완료를 포함한 상태 매핑과 모션은 [캐릭터 적용 기록](design/character/README.md)을 따른다. 아래 Roomie 그림·구 모션 설명보다 이 기록이 우선하며, 앱 팔레트·서체·공식 off 로고는 유지한다. Xcode 약관 미동의로 변경 후 빌드·실제 화면 확인은 대기 중이다.

현재 구현 체크포인트: [R1 실행 기록](R1_IMPLEMENTATION_STATUS.md). 영어 원문·상시 무료 복구·저장과 적용 분리가 반영됐다. 아래 기존 한국어 문안·10초 대기·통행권 예시는 현재 동작이나 확정 상품의 근거가 아니다.

## Native Roomie v2 — 2026-09-07 최신 구현

이 절과 [ContentView.swift](RoomDNS/App/ContentView.swift)의 `RoomSpiritState`, `RoomSpiritReaction`, `RoomSpirit`가 아래 과거 rollout·타이밍의 현재 상태를 대체한다. 기존 SwiftUI Shape를 사용하며 Rive·Lottie·외부 폰트는 추가하지 않았다.

| 현재 연결 | 소스의 반응·조건 |
|---|---|
| Welcome·안내 | 작게 몸을 세우는 첫 인사와 기울이기. 권한·대상·장소의 실제 화면 상태로 준비·확인 자세 선택 |
| 처리 중·집중·오류 | 낮은 대기, 안정된 집중, 정적인 주의 자세. 반복 호흡은 idle·attentive에만 허용 |
| 저장·활성화 | `saveRule` 성공 뒤 작은 점프, `activateRule` 성공 뒤 수긍. 오류·현재 집중·접근 복구 자세가 홈의 일회성 반응보다 우선 |
| 복구 | `safetyRelease()` 뒤 오류가 없으면 중립 복구 반응. 실제 재개 조건은 AppModel 문구를 사용 |
| 예정된 종료 | 집중 상태가 종료되고 규칙은 켜져 있지만 일정 구간 밖이며, 복구·오류 상황이 아닐 때 몸 펴기 |
| 다시 방문 | 로컬 `roomie.lastVisit` 기준 48시간 이상 간격이고 저장 규칙이 있을 때 인사. streak·분석 이벤트·푸시는 추가하지 않음 |
| 직접 탭 | Welcome·Home의 접근성 이름이 있는 버튼에서 인사. 집중·처리·오류·복구 자세에서는 조용한 접근성 인사만 전달하고 규칙은 변경하지 않음 |

반응 샘플은 0.6초 또는 0.8초에 끝나고 안정 자세로 돌아간다. idle·attentive는 5.2초 주기의 호흡·깜빡임을 사용한다. Reduce Motion에서는 이동·변형·반복 모션 대신 정적 자세를 표시한다. scene 비활성화·뷰 소멸·스크롤 가시성 변화에서 반복을 중지하도록 연결한다. 직접 탭은 조용한 상태와 Reduce Motion을 포함해 0.82초 간격으로 제한한다. 타이밍은 앱 로직이나 안전 복구를 지연시키지 않는다. 장식 도형은 VoiceOver에서 숨기고 인사 버튼은 별도로 노출한다.

이 기록은 코드 경로 확인이며 실기기 프레임률·전력·VoiceOver·최대 글자 크기·스크롤 가시성 검증 완료를 의미하지 않는다. 복귀 인사의 효과도 미검증 가설이다. 아래 P0/P1/P2의 과거 미완료 목록과 충돌하면 이 절의 현재 연결과 실행 기록을 따른다.

## Status

- Direction: approved working direction
- Working character name: `룸이 (Roomie)`
- Register: product
- Platform: iPhone, SwiftUI, iOS 18+
- Last reviewed: 2026-09-07 (Native Roomie v2 상태·사건 연결)
- Production decision: native SwiftUI prototype implemented; add Rive only after the native ceiling is demonstrated
- Strategy revision: G1. [ORCHESTRATOR.md](ORCHESTRATOR.md)와 [UX 전략](UX_STRATEGY_REPORT.ko.md)의 영어 우선·글로벌 자기관리 경험에 연결한다. Roomie의 앱 내부 역할과 캐릭터 없는 앱 아이콘 후보를 구분한다. 통행권 모션은 기존 미구현 가설의 참고다.

## Decision

Offzone의 중심 캐릭터는 문 모양 로봇이 아니라, 방에 조용히 머무는 통통한 흰 정령 `룸이`다.

룸이는 사용자를 평가하거나 훈육하지 않는다. 현재 상태를 몸의 무게와 작은 표정으로 보여주고, 설정 과정에서는 다음 행동을 가리키며, 집중이 시작되면 함께 고요해진다. 귀여움은 큰 얼굴 연기나 장식에서 만들지 않고 다음 세 가지에서 만든다.

1. 한눈에 기억되는 넓고 둥근 실루엣
2. 눌리고 늘어난 뒤 조금 늦게 따라오는 몸의 물성
3. 가까이 모인 작은 눈과 건조한 표정

문턱 은유는 없애지 않는다. 룸이의 발 아래 짧은 코발트 선이 장소, 집중 경계, 진행 상태를 담당한다. 캐릭터가 감정을 전달하고 문턱 선이 제품 상태를 전달한다.

## Original Reference Assets

### Character states

![Roomie keyframes](assets/motion/room-spirit-keyframes-v1.png)

이 시트는 사용자가 준 둥근 캐릭터 이미지에서 `통통한 비율`, `작은 표정`, `손으로 그린 듯한 가장자리`라는 원리만 추출해 새로 만든 원본 방향 시트다. 원본 캐릭터의 정확한 윤곽은 복제하지 않았다.

왼쪽 위부터:

1. 숨 쉬는 대기
2. 사용자를 발견
3. 다음 행동 안내
4. 위치 확인 중
5. 권한 또는 설정 완료
6. 집중 모드로 가라앉음
7. 절제된 완료 점프
8. 조치 필요

### Screen placement storyboard

![Roomie screen storyboard](assets/motion/room-spirit-screen-storyboard-v1.png)

왼쪽 위부터 Welcome, Screen Time 설명, 대상 선택, 위치 확인, 집중 중, 설정 완료다. 이 시트는 배치와 움직임의 방향만 정한다. 실제 UI의 텍스트, 컨트롤, 시스템 sheet를 대체하지 않는다.

## What the References Actually Teach

경쟁 앱의 캐릭터를 그대로 복제하지 않고, 반복 가능한 제품 원리만 가져온다. 공개 사례는 좋은 제작 참고이지 Offzone의 잔존율을 보장하는 인과 증거는 아니다. 효과는 우리 온보딩 실험으로 확인한다.

| Reference | Observable pattern | Offzone 적용 | 가져오지 않는 것 |
|---|---|---|---|
| [Duolingo: Reshaping Duo](https://blog.duolingo.com/reshaping-duo/) | 세부보다 먼저 멀리서 읽히는 실루엣을 정함 | 32pt에서도 읽히는 넓은 흰 덩어리 | 초록색, 올빼미, 날개, 동일 비율 |
| [Duolingo: Building character](https://blog.duolingo.com/building-character/) | 캐릭터를 장식이 아니라 핵심 과업과 정답 피드백에 배치 | 권한 완료, 장소 저장, 규칙 완료에 반응 | 모든 탭의 과도한 축하, 학습 게임 문법 |
| [Duolingo: World character animation](https://blog.duolingo.com/world-character-visemes/) | idle, 결과 반응, 표정 레이어를 상태로 나눠 실시간 제어 | 안정 상태와 일회성 사건을 분리 | 입 모양 20개, 음성 동기화 같은 불필요한 규모 |
| [Finch home](https://help.finchcare.com/hc/en-us/articles/37780000231309-Exploring-the-Finch-Home-Page) | 홈의 동반자를 직접 만지고 상태를 확인 | 홈에서 한 번 탭하면 짧게 반응 | 펫 육성, 상점, 의상 수집 |
| [Finch self-care approach](https://help.finchcare.com/hc/en-us/articles/37935669335309-Our-Approach-to-Self-Care) | 작은 행동을 부드럽게 격려하는 동반자 | 실패보다 복구 행동을 안내 | 돌보지 않으면 슬퍼지는 죄책감 루프 |
| [Task Tree](https://www.rive.app/blog/building-a-kooky-adhd-friendly-productivity-app-with-rive) | 진행, 보상, 성격에 각각 움직임을 연결하고 idle에는 잠듦 | 상태에 이유가 있는 모션, 복귀 인사 | 화면 전체 게임화, 수집 경제 |
| [Brilliant animation system](https://www.rive.app/blog/how-brilliant-org-motivates-learners-with-rive-animations) | 실제 진행 수치와 축하 애니메이션을 동기화 | 규칙 저장이 성공한 뒤에만 완료 연기 | 결과 전에 미리 재생되는 가짜 성공 |
| [Rive animated login](https://rive.app/community/files/2244-4437-animated-login-screen/) | 캐릭터 시선이 사용자의 직접 입력에 반응 | 선택 카드나 CTA 방향을 한 번 바라봄 | 시선을 계속 추적하는 감시 같은 연출 |
| [Pinterest Duolingo onboarding](https://in.pinterest.com/pin/919438080179539201/) | 캐릭터, 짧은 대화, CTA 하나의 단순한 첫 화면 | 한 화면 한 질문, 한 행동 | 화면 구성, 카피, 색의 복제 |
| [Apple Motion](https://developer.apple.com/design/human-interface-guidelines/motion) | 모션은 상태, 피드백, 안내를 위해 짧고 취소 가능해야 함 | 기능을 설명하는 동작만 유지 | 기다려야 하는 연출, 상시 주변 움직임 |

## Character Bible

### Shape

- 넓이와 높이 비율은 약 `1.25–1.4 : 1`로 유지한다.
- 머리 위에는 뾰족한 귀가 아니라 높이가 다른 부드러운 돌기 두 개를 둔다.
- 눈은 얼굴 중앙 가까이에 둔 짧은 먹색 캡슐 두 개다.
- 입은 눈보다 짧은 한 획이며, 대부분 무표정에 가깝다.
- 평소 팔다리는 보이지 않는다. 안내할 때만 몸에서 작은 손이 잠깐 늘어난다.
- 발 아래 코발트 선은 캐릭터 그림자가 아니라 `집중 문턱`이다.
- 최종 제품 그래픽은 평면 벡터다. 생성 시트의 미세한 음영과 glow는 사용하지 않는다.

### Personality

- 느긋하다. 사용자를 재촉하지 않는다.
- 약간 건조하게 웃기다. 과장된 표정보다 한 박자 늦은 반응을 쓴다.
- 실패를 실망으로 연기하지 않는다. 문제를 발견하면 고개를 기울이고 해결 행동을 가리킨다.
- 구매 여부에 감정적으로 반응하지 않는다.
- 집중이 잘될수록 더 조용해진다. 보상 때문에 더 시끄러워지지 않는다.

### Never

- 화냄, 울음, 죽음, 시듦, 굶주림
- 사용자가 규칙을 해제했을 때 실망하는 표정
- 결제 버튼을 붙잡거나 가리키는 행동
- 가짜 카운트다운, 코인, 불꽃, 하트 폭죽
- 빠른 무한 튕김, 진동, 회전, 화면 가장자리 배회
- 중요한 텍스트나 버튼을 가리는 배치

## Motion Grammar

한 화면에서 스스로 움직이는 초점은 룸이 하나뿐이다. 다른 UI는 사용자의 입력이나 실제 상태 변화가 있을 때만 반응한다.

| Layer | Duration | Use | Rule |
|---|---:|---|---|
| Touch feedback | system–140ms | 버튼, 카드 선택 | 네이티브 press state를 우선 사용 |
| Micro response | 120–180ms | 눈 깜빡임, 시선, 체크 교체 | 한 번만, 의미를 텍스트와 함께 전달 |
| State change | 180–280ms | 준비, 집중, 조치 필요 | 캐릭터와 코발트 선을 같은 사건에 맞춤 |
| Character beat | 420–720ms | 첫 인사, 성공, 작은 점프 | 입력을 막지 않고 한 번만 재생 |
| Welcome entrance | 최대 850ms | 최초 Welcome 진입 | 다시 방문할 때 반복하지 않음 |
| Ambient idle | 2.8–4.2s + 휴지 | 호흡, 드문 깜빡임 | foreground에서만, 집중 중에는 정지 |

### Physical rules

- 몸통의 기준점은 항상 바닥이다. 크기가 바뀌어도 공중에 떠 보이지 않는다.
- 점프 전에 높이를 `4–6%` 누르고, 위로 `8–10%` 이동하고, 착지 때 높이를 `6–8%` 누른 뒤 원래 형태로 돌아온다.
- 머리 돌기와 눈은 몸보다 `40–70ms` 늦게 따라온다.
- overshoot는 `4%` 이하로 제한한다. 반복 bounce나 elastic curve는 사용하지 않는다.
- 화면 전환보다 캐릭터 반응을 먼저 끝내려고 기다리지 않는다. 사용자는 즉시 다음 단계로 갈 수 있다.
- 실패 피드백에 shake를 쓰지 않는다. 한쪽으로 `2–3°` 기울고 정지한다.

### Easing

- 일반 상태 전환: ease-out-quint에 가까운 감속
- 호흡: 대칭 ease-in-out
- 점프: 빠른 상승, 느린 감속, 짧은 착지 정리의 세 구간 keyframe
- 퇴장은 같은 진입의 약 `75%` 길이
- 시스템 sheet와 navigation은 시스템 모션을 그대로 쓴다.

## State Contract

앱 로직과 애니메이션 타임라인을 섞지 않는다. 앱은 안정 상태와 사건만 전달하고, 뷰가 표현을 결정한다.

### Stable states

| State | Body | Face | Cobalt threshold |
|---|---|---|---|
| `idle` | 원래 높이, 드문 호흡 | 무표정, 드문 blink | 짧은 고정선 |
| `attentive` | 2% 세움 | 시선이 다음 행동 방향으로 이동 | 고정선 |
| `working` | 8–12% 낮게 눌림 | 눈을 좁히고 대기 | 선 위 작은 진행점 1개 |
| `focused` | 넓고 무겁게 가라앉음 | 눈을 감음 | 몸 아래에서 짧고 안정적 |
| `passActive` | 약간 다시 열림 | 중립 | 남은 시간에 따라 길이 감소 |
| `needsAction` | 2–3° 기울어짐 | 한 눈만 조금 올라감 | warning 색, 정적 |

### One-shot events

| Event | Motion | Trigger condition |
|---|---|---|
| `notice` | 위를 보고 몸이 3% 세워짐 | 최초 Welcome 등장 |
| `guide` | 작은 손이 CTA 방향으로 10% 늘었다 복귀 | 사용자가 2초 이상 머물고 다음 행동이 명확할 때 한 번 |
| `confirm` | 4% 들렸다 부드럽게 착지 | 실제 권한, 장소, NFC 결과 성공 뒤 |
| `celebrate` | 작은 점프 1회 | 규칙 저장 성공 뒤 |
| `recover` | 기울었던 몸이 중앙으로 돌아옴 | 권한 또는 위치 문제 해결 뒤 |

`success`를 버튼 탭 시점에 보내지 않는다. AppModel의 실제 성공 상태가 확인된 뒤에만 `confirm`이나 `celebrate`를 보낸다.

## Screen-by-Screen Application

| Screen or moment | Character | Nearby motion | Do not do |
|---|---|---|---|
| Welcome | 큰 `idle` → `notice` | 제목과 CTA는 정적 | 매번 긴 인트로 재생 |
| Screen Time 설명 | 작은 `attentive` | 권한 카드 방향으로 시선 1회 | 시스템 승인 sheet 위에 커스텀 캐릭터를 얹는 척하기 |
| 승인 결과 | `confirm` | shield symbol 짧은 교체 | 성공 전 축하 |
| 대상 선택 전 | 작은 `guide` | 선택 카드 border 160ms | 여러 앱 아이콘이 계속 떠다님 |
| Apple 대상 picker | 표시하지 않음 | 시스템 UI 그대로 | opaque 선택 정보를 캐릭터가 읽는 듯 표현 |
| 대상 picker 복귀 | `confirm` | 선택 개수 교차 페이드 | 선택 앱 로고를 캐릭터 주변에 노출 |
| GPS 위치 확인 | `working` | 코발트 진행점 하나 | 레이더, 지도 핀 펄스, 정확한 방 감지 암시 |
| NFC 대기 | `attentive` | 태그를 댈 방향을 한 번 안내 | 자동 방 감지처럼 보이는 파동 |
| 시간 설정 | `idle` | DatePicker 시스템 반응만 | 눈으로 휠을 계속 추적 |
| 검토 | 정적 `attentive` | 없음 | 저장 전 축하 |
| 준비 완료 | `celebrate` | 코발트 길이 한 번 확장 | 폭죽과 보상 재화 |
| Home: 장소 밖 | 큰 `idle` | 드문 호흡 | 의미 없는 배경 입자 |
| Home: 준비됨 | `attentive` | 코발트 선이 정렬 | 반복 손짓 |
| Home: 집중 중 | `focused` | 거의 완전 정지 | 집중 중 춤, 상시 blink |
| Home: 조치 필요 | `needsAction` | 해결 CTA 강조 1회 | 흔들기, 경고 깜빡임 |
| 15분 통행권 | 중립 `passActive` | 선 길이가 시간에 맞춰 감소 | 슬픈 얼굴, 구매 압박, 코인 |
| 구매 성공 | `confirm` | 실제 entitlement와 해제 확인 뒤 선이 열림 | StoreKit 성공 전에 결과 연기 |
| 구매 취소·실패 | 정적 중립 | 오류 텍스트만 | 실망, 죄책감, 재구매 손짓 |
| 무료 안전 해제 | 정적 `attentive` 또는 숨김 | 진행 바와 텍스트 우선 | 캐릭터가 10초를 방해하거나 압박 |

## Apple-Controlled Surfaces

- `FamilyActivityPicker`는 Apple이 제공하는 선택 화면이다. 캐릭터는 picker가 열리기 전과 닫힌 뒤에만 반응한다.
- 개인 Screen Time 승인은 시스템 인증 화면이다. 승인 화면 자체에 캐릭터를 넣지 않는다.
- Managed Settings shield는 `UIImage` icon, 색, 제목, 버튼 같은 제한된 구성만 받는다. 움직이는 SwiftUI/Rive 캐릭터를 shield 안에 넣는 것으로 설계하지 않는다.
- shield에는 룸이의 정적 1색 얼굴 또는 실루엣만 쓴다. 사용자가 메인 앱으로 돌아오면 그때 반응을 이어간다.

관련 Apple 문서: [FamilyActivityPicker](https://developer.apple.com/documentation/familycontrols/familyactivitypicker), [ShieldConfiguration](https://developer.apple.com/documentation/managedsettingsui/shieldconfiguration).

## Implementation Choice

### MVP: native SwiftUI

현재 캐릭터는 몸통 1개, 머리 돌기 2개, 눈 2개, 입 1개, 코발트 선 1개로 충분하다. 기존 프로젝트에 이미 SwiftUI 상태 그래픽이 있고 별도 애니메이션 의존성이 없다. 따라서 첫 구현은 다음 네이티브 API로 끝낸다.

- `Shape` 또는 `Canvas`: 몸통과 얼굴
- `phaseAnimator`: 호흡과 드문 blink
- `keyframeAnimator`: notice, confirm, celebrate
- 기본 SwiftUI transition: 카드, 상태 텍스트, 코발트 선
- `accessibilityReduceMotion`: 정적 최종 자세와 짧은 cross-fade

추가 패키지, 애니메이션 이벤트 버스, 범용 모션 프레임워크는 만들지 않는다. 기존 `ThresholdField`를 `RoomSpirit`로 교체했고 AppModel의 기존 상태를 그대로 입력으로 사용한다.

### Rive: 다음 조건 중 하나가 실제로 생길 때만

1. 애니메이터가 코드 배포 없이 포즈와 타이밍을 반복 수정해야 한다.
2. 몸 변형, 시선, 손, 표정을 동시에 섞는 레이어가 네이티브 keyframe보다 복잡해진다.
3. 동일 캐릭터를 iOS 밖의 런타임에서도 공유한다.

Rive를 쓰게 되면 한 artboard와 한 state machine만 유지한다. 입력 계약은 `status` 숫자 하나와 `notice`, `guide`, `confirm`, `celebrate` trigger 네 개로 제한한다. Rive는 상태와 전환을 asset 안에서 관리할 수 있고 Apple 런타임과 SwiftUI를 지원한다. [Rive state machines](https://rive.app/docs/editor/state-machine/state-machine), [Rive Apple runtime](https://rive.app/docs/runtimes/apple/apple).

### Why not Lottie for this mascot

Lottie는 한 번 재생되는 성공 장면과 마케팅 일러스트에는 적합하다. 하지만 룸이는 앱 상태 사이를 즉시 오가고 idle과 사건 반응을 섞어야 한다. 타임라인 재생 중심 자산을 여러 개 관리하는 것보다 SwiftUI 상태 또는 Rive state machine이 단순하다. 따라서 MVP와 이후 캐릭터 런타임 모두에 Lottie를 추가하지 않는다. [Lottie iOS](https://github.com/airbnb/lottie-ios).

## Accessibility and Resource Rules

### Reduce Motion

- `accessibilityReduceMotion == true`면 이동, squash, 점프, 회전, 반복 idle을 끈다.
- 캐릭터를 없애지는 않는다. 의미가 있는 최종 정적 자세와 상태 텍스트를 보여준다.
- 상태 변경은 `100–120ms` opacity 또는 색 교체로 대체한다.
- 모션 설정이 권한 요청, 차단, 해제, 타이머의 실제 시점을 바꾸면 안 된다.
- 앱 내 별도 토글을 만들지 않고 iOS 시스템 설정을 따른다.

Apple은 scaling, spinning, depth simulation, ongoing motion을 Reduce Motion에서 중단하거나 대체하도록 안내한다. [Reduced Motion criteria](https://developer.apple.com/help/app-store-connect/manage-app-accessibility/reduced-motion-evaluation-criteria), [SwiftUI environment values](https://developer.apple.com/documentation/swiftui/environmentvalues).

### VoiceOver

- 룸이는 기본적으로 `accessibilityHidden(true)`다.
- 상태 의미는 기존 제목과 상세 텍스트가 말한다.
- 룸이를 탭해 반응시키는 Easter egg를 추가하더라도 핵심 기능은 두지 않는다.
- 캐릭터가 가리키는 방향만으로 다음 행동을 설명하지 않는다.

### Runtime

- scene이 inactive 또는 background면 반복 모션을 멈춘다.
- 동시에 자동 재생되는 캐릭터는 하나뿐이다.
- blur, glow, 입자, 대형 shadow를 캐릭터에 사용하지 않는다.
- 실제 최소 지원 기기에서 Core Animation과 SwiftUI Instruments로 hitch를 확인한다.
- 60fps를 목표로 하되 모션 때문에 위치, Screen Time, 안전 해제 로직의 응답이 늦어지면 모션을 제거한다.

## Production Asset Contract

- 기준 artboard: `320 × 240pt`, transparent background
- small variant: 같은 실루엣을 `72–104pt` 높이에서 사용
- hero variant: `180–240pt` 높이
- 바닥 anchor와 얼굴 중심은 모든 상태에서 유지
- 색은 `roomInk`, `roomCanvas`, `roomAccent`, `roomWarning` token만 사용
- 텍스트, 로고, 실제 앱 아이콘, 장소 정보는 asset 안에 넣지 않음
- 파일 이름과 event 이름은 영어 ASCII로 고정
- generated PNG는 콘셉트 참고용이고 앱 번들 최종 에셋으로 사용하지 않음

## Rollout

### P0 — one vertical slice

1. 완료: `ThresholdField`를 네이티브 `RoomSpirit` 형태로 교체
2. 완료: Welcome 진입, idle 호흡·blink, 안내, 작업 중, 완료, 집중, 조치 필요 자세 연결
3. 완료: Reduce Motion과 background에서 반복 모션 정지
4. 남음: 완료 점프 keyframe과 실제 iPhone의 60fps, VoiceOver, AX5 확인

### P1 — onboarding feedback

1. permission, target, place 화면에 small variant 배치
2. 실제 성공 뒤 `confirm`, 위치 대기 중 `working` 연결
3. 시스템 picker와 권한 화면 복귀 시 이벤트 중복 재생 방지

### P2 — only after measurement

1. 홈 탭 반응과 드문 idle 변형
2. Rive 전환 필요성 평가
3. 계절 의상, 수집, 여러 캐릭터는 출시 범위에서 제외

## Validation

모션이 예뻐 보이는지만 보지 않는다.

- Welcome → Screen Time 설명 진입률
- 각 온보딩 단계 완료율과 이탈 지점
- 첫 규칙 저장까지 걸린 시간
- 권한 거부 뒤 복구율
- 캐릭터가 CTA 이해를 방해했다는 사용성 관찰
- Reduce Motion에서 전체 흐름 완료 가능 여부
- 실제 기기 animation hitch와 energy impact
- 집중 중 캐릭터 때문에 다시 앱을 여는 행동이 늘지 않는지

첫 실험은 `캐릭터 있음/없음`처럼 브랜드 자체를 흔들지 않는다. 같은 캐릭터에서 Welcome의 `notice` 유무 하나만 비교한다. 전환이 올라도 설정 완료 시간, 권한 철회, 안전 해제가 나빠지면 실패로 본다.

## Kiwi video loop — 2026-09-22
Approved Veo Lite clip is bundled at `RoomDNS/KiwiMotion/kiwi-pingpong.mov`: 480×376, 24fps, 382 frames (~15.92s), silent, forward then reverse without duplicated endpoints. Welcome/idle/returning/guiding/working use AVPlayerLooper; focus/action/error/completion preserve semantic state illustrations. Reduce Motion uses kiwi-poster.png; offscreen/background pauses. This is reversed walking, not a generated turn. The movie uses native HEVC with alpha and the poster uses PNG alpha. No white tile or background is rendered.
