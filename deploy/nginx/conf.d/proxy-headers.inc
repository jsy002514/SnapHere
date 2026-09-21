# 프록시 공통 헤더. api.conf 의 각 location 이 include 한다.
#
# app 의 SERVER_FORWARD_HEADERS_STRATEGY=NATIVE 가 이 헤더들을 읽는다.
# X-Forwarded-Proto 가 없으면 서버가 만드는 절대 URL 이 http:// 로 나가고,
# X-Forwarded-For 가 없으면 로그의 클라이언트 IP 가 전부 nginx 로 찍힌다.
proxy_http_version 1.1;
proxy_set_header Host              $host;
proxy_set_header X-Real-IP         $remote_addr;
proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host  $host;
proxy_set_header Connection        "";
