
java -jar findBadLinks.jar read_abused_files 
pause

java -jar findBadLinks.jar analyse_wp_links 
pause

java -jar findBadLinks.jar full_restore_circle
pause

java -jar findBadLinks.jar restore_abused_files
pause

java -jar findBadLinks.jar add_posts
pause

java -jar findBadLinks.jar grab_new_series
pause

mvn clean package -Dmaven.test.skip=true
mvn -Dtest=GoogleTest#testUpdateConfig test

https://stackoverflow.com/questions/3685548/java-keytool-easy-way-to-add-server-cert-from-url-port

=TRIM(Left(REGEXEXTRACT(A19; ".+? S\d"); LEN(REGEXEXTRACT(A19; ".+? S\d")) -2))
=REGEXEXTRACT(A19; "S(\d{1,2})")
=REGEXEXTRACT(A19; "S\d{1,2}E(.*?):")
=TRIM(RIGHT(REGEXEXTRACT(A19; ": .*"); LEN(REGEXEXTRACT(A19; ": .*"))-2))
