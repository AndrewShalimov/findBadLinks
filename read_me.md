
java -jar findBadLinks.jar read_abused_files 
pause

java -jar findBadLinks.jar analyse_wp_links 
pause

java -jar findBadLinks.jar full_restore_circle
pause

mvn clean package -Dmaven.test.skip=true
mvn -Dtest=GoogleTest#testUpdateConfig test

https://stackoverflow.com/questions/3685548/java-keytool-easy-way-to-add-server-cert-from-url-port