set "HOST=stream-tv-series.xyz"
set "PORT=443"

openssl s_client -connect %HOST%:%PORT% > null | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > %HOST%.cert