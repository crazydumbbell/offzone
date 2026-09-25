# Nook Cat 이미지 생성 지시 요약

이 문서는 실제 `image_gen` 호출의 **요약**이다. 호출마다 문장을 조금씩 조정했으므로 아래 문구를 원문 프롬프트로 취급하지 않는다.

## 입력과 공통 기준

- OpenAI 내장 `image_gen`을 사용해 **상태마다 한 번씩** 생성했다.
- 참조 이미지는 [사용자가 고른 F 원본](selected-f-original.png)과 그로부터 생성된 투명 컷아웃/표정 앵커였다. Pinterest 이미지는 생성기에 입력하지 않았다.
- 크림색 고양이, 큰 둥근 머리, 짙은 솔잎색 눈, 분홍빛 귀·코·볼, 흑연색 수염, 솔잎색 케이블 니트 민소매 터틀넥을 유지했다.
- 색연필·파스텔 종이 결, 정사각형 RGBA, 캐릭터 중심의 타이트한 구도를 요청했다. 장면·후광·그림자·소품·문자는 제외하도록 지시했다.

## 상태별 변형

| 파일 | 동작·표정 지시 요약 |
|---|---|
| `portraits/welcome.png` | 손을 흔들고 입을 연 반가운 미소 |
| `portraits/ready.png` | 두 손을 모은 차분한 준비 상태 |
| `portraits/focused.png` | 살짝 내린 부드러운 시선 |
| `portraits/reflection.png` | 기운 귀와 턱에 댄 손, 돌아보는 표정 |
| `portraits/finished.png` | 감은 눈으로 기뻐하며 두 손을 올림 |
| `portraits/recovered.png` | 가슴에 손을 얹은 안도감 |
| `portraits/needs-action.png` | 몸을 기울이고 손을 내밀어 조용히 안내함 |
| `portraits/failed.png` | 귀를 낮춘 미안한 표정 |
| `fullbody/welcome.png` | 앉은 전신, 한 손 인사 |
| `fullbody/ready.png` | 앉은 전신, 두 손을 모아 준비 |
| `fullbody/focused.png` | 앉은 전신, 집중한 자세와 시선 |
| `fullbody/recovered.png` | 앉은 전신, 가슴의 손과 밖으로 여는 손짓 |

완성 PNG의 경로·크기·SHA-256과 iOS·Android 복사 위치는 [manifest.json](manifest.json)에 기록했다. 알파 채널 존재만으로 가장자리 품질을 보증하지 않으므로 [디자인보드](index.html)의 배경 전환과 앱 화면 캡처로 검수한다.
