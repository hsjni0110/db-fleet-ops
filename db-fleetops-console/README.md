# DB FleetOps 운영 콘솔

실행 및 API 설정은 [프로젝트 README의 로컬 실행 절차](../README.md#로컬-실행)에 따른다.

## 화면 문구와 표시 기준

- 메뉴와 제목에 한국어 명사형을 사용한다. 메뉴 이름은 [경로 정의](src/router/index.ts)의 화면 제목에서 읽는다.
- 작업 유형과 상태의 화면 표기는 [운영 용어](src/labels.ts)에서 관리한다. API 상태값과 오류 코드는 상세 조사에 사용할 수 있도록 원본 값을 사용한다.
- 화면에 상시 노출할 정보는 이름과 결과를 중심으로 구성한다. 입력 형식이나 낯선 개념의 설명은 이름 옆의 [도움말](src/components/FieldHelp.vue)에 둔다. 마우스를 올리거나 키보드로 초점을 맞추면 설명을 확인할 수 있다.
- [공통 스타일](src/styles/global.css)은 흰색과 회색을 기본으로 사용한다. 청색은 선택과 주요 동작에 사용하고, 성공·주의·실패는 색과 상태 이름으로 함께 표시한다. 작은 화면에서 표는 자체 영역에서 좌우로 이동한다.
- 복원 검증 화면은 판정과 오류를 앞에 둔다. 백업 파일과 실행 단계 번호는 `백업 파일 및 실행 기록`에서 확인한다.

## 디자인 참고 자료

| 참고 자료 | 적용한 기준 |
| --- | --- |
| [AWS RDS 인스턴스 상태](https://docs.aws.amazon.com/ko_kr/AmazonRDS/latest/UserGuide/accessing-monitoring.html) | 데이터베이스 목록에 이름과 상태를 함께 표시 |
| [IBM Carbon 데이터 표](https://carbondesignsystem.com/components/data-table/usage/) | 표 제목과 조회 동작을 머리 영역에 배치 |
| [GitHub Primer 색상 사용](https://primer.style/product/getting-started/foundations/color-usage/) | 배경·테두리·글자·강조색의 역할 구분 |
| [PatternFly 상태 표시](https://www.patternfly.org/components/label/design-guidelines/) | 상태 색상에 읽을 수 있는 이름을 함께 표시 |
| [Grafana 표 시각화](https://grafana.com/docs/grafana/latest/visualizations/panels-visualizations/visualizations/table/) | 첫 화면의 요약 지표와 세부 기록 표 배치 |

## 검증 범위

- TypeScript 검사와 프로덕션 빌드 통과
- 13개 경로의 초기 HTML 렌더링 확인
- 실제 API에 연결된 운영 현황, 데이터베이스, 작업, 실행기, 설정 및 복원 검증 화면 확인. [화면 캡처](../README.md#화면에서-확인할-수-있는-것)
- 좁은 화면에서 표를 이동하는 동작과 도움말 조작은 별도 확인 필요
