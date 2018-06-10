
java -jar findBadLinks.jar read_abused_files 
pause

java -jar findBadLinks.jar analyse_wp_links 
pause

java -jar findBadLinks.jar full_restore_circle
pause

mvn -Dtest=GoogleTest#testUpdateConfig test