# Nook Cat · 앱 자산 기록

## 선택과 출처

- 사용자가 [10종 디자인보드의 F, Nook Cat](../2026-09-25-cute-10x10/index.html#candidate-F)을 최종 선택했다.
- 기준 파일은 보드 안에 복사한 [`selected-f-original.png`](selected-f-original.png)이며 SHA-256은 `ca0a282cd9209ce7b63a4d989231cb932c7f9cdab575a3162eba516e95fbe07d`이다. 원본에는 버터색 장면 배경이 있다.
- 기준 파일은 Offzone 10종 탐색용으로 생성된 시안이다. 당시 Pinterest 자료는 비교·출처 표시에만 사용됐고 생성기 입력에는 포함되지 않았다. 이번 상태 이미지 생성에도 Pinterest 이미지를 입력하지 않았다.
- 이번 앱 팩은 OpenAI 내장 `image_gen`으로 원본 F와 생성된 Nook 앵커를 참조해 새로 만든 PNG 12개다. 세부 지시는 [PROMPTS.md](PROMPTS.md), 파일별 해시와 복사 위치는 [manifest.json](manifest.json)을 따른다.

## 사용

| 형태 | 수량 | 앱 배치 |
|---|---:|---|
| 표정 | 8 | iOS 작은 슬롯과 전신 매핑이 없는 상태; Android 온보딩의 뒤 두 단계 |
| 전신 | 4 | iOS 넓은 슬롯의 환영·준비·집중·복구; Android 첫 온보딩과 홈의 준비·집중 |

iOS는 `RoomDNS/Assets.xcassets/OffzoneNook-*`, Android는 `android/app/src/main/res/drawable-nodpi/nook_*`에 동일 PNG가 복사됐다. 상태별 사용 여부와 표시 크기는 네이티브 코드가 결정한다. Android는 자산 12개를 번들에 넣었지만 현재 화면에서 모든 상태가 노출되지는 않는다.

## 품질 메모

- PNG 12개는 알파 채널을 가진 1254 × 1254 파일이다. 원본 F의 장면 배경은 앱 자산으로 복사하지 않았다.
- 단독 투명 이미지 뷰어에서 일부 윤곽에 색 프린지가 보인다. 그러나 [iOS Simulator 4장과 Android emulator 2장](SCREEN_REVIEW.md)의 실제 앱 합성 화면에서는 원본 사각 배경이나 빨강·노랑 테두리 번짐이 보이지 않았다.
- 이 보드는 상태별 아트와 축소 미리보기의 비교 도구다. 미관찰 상태·배경과 실기기의 가독성·접근성·출시 적합성은 별도 검수가 필요하다.
