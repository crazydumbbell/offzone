# Nook Cat 실제 화면 디자인 루프 · 2026-09-25

기준: `.claude/skills/room-mascot-design/SKILL.md`의 100점 루브릭. 점수는 캡처에 대한 시각 평가이며 기능·실기기 검증 점수가 아니다.

| 반복 | 관찰 화면 | 원본 일치 /30 | 배경·가장자리 /25 | 시각 순서 /20 | 상태 일관성 /15 | 축소·접근성 /10 | 합계 |
|---|---|---:|---:|---:|---:|---:|---:|
| iOS 1 | 설정 단계 수정 전 화면(작업 기록, 이 공개 팩에는 미포함) | 28 | 16 | 19 | 13 | 8 | **84** |
| iOS 2 | [설정](screenshots/ios-setup-en.png), [첫 화면](screenshots/ios-welcome-en.png), [복구](screenshots/ios-recovery-en.png), [홈](screenshots/ios-home-en.png) | 28 | 24 | 18 | 13 | 9 | **92** |
| Android 1 | [첫 화면](screenshots/android-welcome.png), [홈](screenshots/android-home.png); 목표·시간대 단계는 작업 중 확인했으나 공개 팩에는 미포함 | 29 | 24 | 19 | 13 | 9 | **94** |

링크한 6장은 이 보드의 `screenshots/`에 포함된 실제 iOS Simulator·Android emulator 화면이다. iOS 1에서 설정 단계의 이미지 뒤 SwiftUI 둥근 배경이 별도 스티커처럼 보였다. 가장 영향이 큰 이 한 가지를 제거해 같은 단계에서 다시 캡처했다. 공개 팩에 포함된 iOS 2와 Android 화면에서는 원본 장면의 사각 배경이나 투명 가장자리 색 번짐이 보이지 않았고, 제목·주요 버튼·무료 복구 경로가 읽힌다. 원본 F의 얼굴, 녹색 눈, 니트 조끼도 유지된다.

검증: `python3 tools/check_nook_assets.py` PASS, Android 오프라인 debug/androidTest 빌드 및 계측 2/2, iOS Nook 상태/자산 테스트 1/1과 최종 증분 빌드. 테스트는 실제 화면 평가를 대신하지 않는다. iOS 테스트는 설정 배경 제거 전에, 증분 빌드/최종 설정 캡처는 제거 후에 수행했다. iOS 준비·집중 등 Screen Time 권한이 필요한 화면과 두 플랫폼 실기기는 이번에 관찰하지 않았다. 권한을 임의 허용/거절하지 않았다.
