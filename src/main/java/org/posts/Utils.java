package org.posts;

import com.google.common.base.Joiner;
import com.google.common.primitives.Ints;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.util.UriUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class Utils {

    private static final class TrivialToStringMaker implements ToStringMaker {

        TrivialToStringMaker() {
            super();
        }

        @Override
        public String toString(Object o) {
            return o.toString();
        }

    }


    public static boolean isEmpty(List list) {
        return null == list || list.size() == 0;
    }

    public static final TrivialToStringMaker TRIVIAL_TOSTRING_MAKER = new TrivialToStringMaker();
    public static final char QUOTE_CHARACTER_SQL = '\'';
    private static Character cvsSplitBy = ';';

    private static final Log logger = LogFactory.getLog(Utils.class);

    /*   extracts the root of route :
        for ex:
        from "/1/countrysubdivisions/22"
        it makes "countrysubdivisions"
     */
    public static String getRouteRoot(String route) {
        if (Utils.isEmpty(route)) {
            return "";
        }
        String[] sub = route.split("/");
        if (sub.length == 2) {
            return sub[1].trim();
        }
        if (sub.length > 2) {
            return sub[1].trim().concat("/");
        }
        return "";
    }

    public static String getRouteRootOnRoutePattern(String route) {
        if (Utils.isEmpty(route)) {
            return "";
        }
        String result = null;
        List<String> paths = Arrays.asList(route.split("/"));
        for (String path : paths) {
            if (!path.startsWith("$") && !path.startsWith(":") && !path.startsWith("]")) result = path.trim();
        }
        return (result != null) ? result : "";
    }

    public static boolean isEmpty(String string) {
        return null == string || string.length() == 0;
    }


    public static String processFilename(String filename) {
        String result = filename.replace(" ", "_").replace("+", "_");
        result = result.replaceAll("[!@#$%^&*(),:'\"~]", "_");
        result = result.replaceAll("[^\\x00-\\x7F]", String.valueOf(getRandomChar()));
        return result;
    }


    public static char getRandomChar() {
        String s = new Long(System.currentTimeMillis()).toString();
        return s.charAt(s.length() - 1);
    }


    public static boolean isEmpty(String[] strings) {
        return null == strings || strings.length == 0;
    }

    public static boolean isDefined(String string) {
        return !isEmpty(string) && !"undefined".equals(string) && !"null".equals(string);
    }

    public static boolean isNumeric(String inputData) {
        return !isEmpty(inputData) && inputData.matches("[-+]?\\d+(\\.\\d+)?");
    }

    public static boolean isAllNumeric(List<String> inputList) {
        boolean shouldBe = isNumeric(inputList.get(0));
        for (String input : inputList) {
            if (shouldBe != isNumeric(input)) throw new RuntimeException();
        }
        return shouldBe;
    }

    public static boolean isZeroOrNegative(Long id) {
        return (id == 0 || id < 0);
    }

    public static void addNumericParameterToMap(String key, String[] values, Map<String, List<String>> map) {
        for (String value : values) {
            if (isNumeric(value)) {
                if (map.get(key) == null) {
                    map.put(key, new ArrayList<String>());
                }
                map.get(key).add(value);
            }
        }
    }

    public static String generateRandomFilenameWithCurrentDate(String filePattern) {
        SimpleDateFormat simpleFormatter_date = new SimpleDateFormat("yyyy-MM-dd");

        StringBuilder fileName = new StringBuilder(simpleFormatter_date.format(new Date())).append("_");
        if (!isEmpty(filePattern)) {
            fileName.append(filePattern).append("_");
        }

        int randomInt;
        int minValue = 48; // {Digits 0-9 : 48-57}
        int maxValue = 57;

        int currentFileNameLength = fileName.length();
        do {
            randomInt = (int) Math.floor((Math.random() * (maxValue - minValue + 1) + minValue));
            fileName.append((char) randomInt);
        } while (fileName.length() < currentFileNameLength + 5);

        fileName.append(".");
        currentFileNameLength = fileName.length();
        do {
            randomInt = (int) Math.floor((Math.random() * (maxValue - minValue + 1) + minValue));
            fileName.append((char) randomInt);
        } while (fileName.length() < currentFileNameLength + 5);

        return fileName.toString();
    }



    public static String escapeCharacterInString(String str, char charToEscape, char escapeChar) {
        int idx = str.indexOf(charToEscape);
        if (idx == -1) {
            return str;
        }

        String strToEscape = new StringBuilder().append(charToEscape).toString();
        StringBuilder cbuf = new StringBuilder(str);
        int lastIdx = 0;
        while (-1 != (idx = cbuf.indexOf(strToEscape, lastIdx))) {
            cbuf.insert(idx, escapeChar);
            lastIdx = idx + 2;
        }
        return cbuf.toString();
    }

    public static String escapeCharacter(String sqlToEscape) {
        if (sqlToEscape == null) {
            return null;
        }
        return escapeCharacterInString(sqlToEscape, QUOTE_CHARACTER_SQL, QUOTE_CHARACTER_SQL);
    }


    public static String unEscape(String s) {
        StringBuffer sbuf = new StringBuffer();
        int l = s.length();
        int ch = -1;
        int b, sumb = 0;
        for (int i = 0, more = -1; i < l; i++) {
            /* Get next byte b from URL segment s */
            switch (ch = s.charAt(i)) {
                case '%':
                    ch = s.charAt(++i);
                    int hb = (Character.isDigit((char) ch)
                            ? ch - '0'
                            : 10 + Character.toLowerCase((char) ch) - 'a') & 0xF;
                    ch = s.charAt(++i);
                    int lb = (Character.isDigit((char) ch)
                            ? ch - '0'
                            : 10 + Character.toLowerCase((char) ch) - 'a') & 0xF;
                    b = (hb << 4) | lb;
                    break;
                case '+':
                    b = ' ';
                    break;
                default:
                    b = ch;
            }
            /* Decode byte b as UTF-8, sumb collects incomplete chars */
            if ((b & 0xc0) == 0x80) {            // 10xxxxxx (continuation byte)
                sumb = (sumb << 6) | (b & 0x3f);    // Add 6 bits to sumb
                if (--more == 0) sbuf.append((char) sumb); // Add char to sbuf
            } else if ((b & 0x80) == 0x00) {        // 0xxxxxxx (yields 7 bits)
                sbuf.append((char) b);            // Store in sbuf
            } else if ((b & 0xe0) == 0xc0) {        // 110xxxxx (yields 5 bits)
                sumb = b & 0x1f;
                more = 1;                // Expect 1 more byte
            } else if ((b & 0xf0) == 0xe0) {        // 1110xxxx (yields 4 bits)
                sumb = b & 0x0f;
                more = 2;                // Expect 2 more bytes
            } else if ((b & 0xf8) == 0xf0) {        // 11110xxx (yields 3 bits)
                sumb = b & 0x07;
                more = 3;                // Expect 3 more bytes
            } else if ((b & 0xfc) == 0xf8) {        // 111110xx (yields 2 bits)
                sumb = b & 0x03;
                more = 4;                // Expect 4 more bytes
            } else /*if ((b & 0xfe) == 0xfc)*/ {    // 1111110x (yields 1 bit)
                sumb = b & 0x01;
                more = 5;                // Expect 5 more bytes
            }
        }
        return sbuf.toString();
    }

    public static String collectionToDelimitedString(Collection<?> c, String delimiter) {
        return collectionToDelimitedString(c, TRIVIAL_TOSTRING_MAKER, delimiter);
    }

    public static String collectionToDelimitedString(Collection<?> c) {
        return collectionToDelimitedString(c, TRIVIAL_TOSTRING_MAKER);
    }

    public static String[] delimitedStringToStringArray(String strDelimitedString, char cDelimiter) {
        return delimitedStringToStringArray(strDelimitedString, cDelimiter, false);
    }

    public static String[] delimitedStringToStringArray(String strDelimitedString, char cDelimiter, boolean trim) {

        if (strDelimitedString == null) {
            return null;
        }
        StringTokenizer st = new StringTokenizer(strDelimitedString, "" + cDelimiter);

        String strings[] = new String[st.countTokens()];
        int i = 0;
        while (st.hasMoreTokens()) {
            strings[i++] = trim ? st.nextToken().trim() : st.nextToken();
        }
        return strings;
    }

    public static String collectionToDelimitedString(Collection<?> c, ToStringMaker tsm) {
        return collectionToDelimitedString(c, tsm, ',');
    }

    public static String collectionToDelimitedString(Collection<?> c, ToStringMaker tsm, char delim) {
        return collectionToDelimitedString(c, tsm, String.valueOf(delim));
    }

    public static String collectionToDelimitedString(Collection<?> c, ToStringMaker tsm, String delim) {
        StringBuffer sb = new StringBuffer(10 * c.size());
        Iterator<?> it = c.iterator();
        while (it.hasNext()) {
            if (sb.length() > 0) {
                sb.append(delim);
            }
            sb.append(tsm.toString(it.next()));
        }
        return sb.toString();
    }

    public interface ToStringMaker {
        String toString(Object o);
    }

    /**
     * Read a file into a String without all the silly buffering. this reads in 1k char chunks
     *
     * @param path
     * @return
     * @throws IOException
     */
    public static String readStringFromFile(String path) throws IOException {
        int bufSize = 1024;
        StringBuffer s = new StringBuffer(2048);
        FileReader fr = new FileReader(path);
        try {
            char c[] = new char[bufSize];
            int cnt = 0;
            while (cnt != -1) {
                cnt = fr.read(c, 0, bufSize);
                if (cnt != -1)
                    s.append(c, 0, cnt);
            }
            return s.toString();
        } finally {
            close(fr);
        }
    }


    public static List<String> readStringsFromFile(File file) throws IOException {
        return readStringsFromFile(file.getAbsolutePath());
    }

    public static List<String> readStringsFromFile(String path) throws IOException {
        Scanner scanner = null;
        ArrayList<String> list = new ArrayList<String>();
        try {
            scanner = new Scanner(new File(path));
            while (scanner.hasNextLine()) {
                list.add(scanner.nextLine());
            }
        } catch (Exception e) {
            logger.error(e);
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }

        return list;
    }

    /**
     * Closes the Closeable, eating any exceptions and doing nothing when the specified stream is <code>null</code>.
     *
     * @param in the <code>InputStream</code> to be closed
     */
    public static void close(Closeable in) {
        if (null == in)
            return;

        try {
            in.close();
        } catch (Throwable t) {
            // silent fail
        }
    }

    /**
     * Similar to the method <code>String.trim()</code> except it trims all leading and trailing whitespace characters,
     * not just space characters. Interestingly, the documentation for <code>String.trim()</code> claims to perform this
     * function but actually only trims space characters, not white space.
     *
     * @param string the string to be trimmed of white space
     * @return a copy of the supplied string with leading and trailing white space removed, or the supplied string if it
     *         has no leading or trailing white space
     * @throws NullPointerException if <code>string</code> is <code>null</code>
     */
    public static String trimWhiteSpace(String string) {
        if (null == string) {
            throw new NullPointerException();
        }

        char[] val = string.toCharArray();
        int len = val.length;
        int st = 0;

        while ((st < len) && Character.isWhitespace(val[st])) {
            st++;
        }
        while ((st < len) && Character.isWhitespace(val[len - 1])) {
            len--;
        }
        return ((st > 0) || (len < val.length)) ? new String(val, st, len - st) : string;
    }

    /**
     * return an empty String if the arg is null, otherwise, return a the String
     *
     * @param str
     * @return the empty or trimmed String
     */
    public static final String safeString(String str) {
        return (null != str) ? str : "";
    }


    /**
     * this method writes a String to a file. If the given file path doesn't exist,
     * the method will create it (including the parent path).
     * Note: this method is going to overwrite(NOT APPEND) the given file if it already exists.
     *
     * @param data
     * @param file
     */
    public static void writeStringToFile(String data, File file) {
        String trimmedData = trimWhiteSpace(safeString(data));
        if (isEmpty(trimmedData)) {
            return;
        }
        FileOutputStream out = null;
        try {
            boolean isEnd = false;
            File tmpFile = file;
            List<File> pathList = new ArrayList<File>();
            while (!isEnd) {
                if (null != tmpFile.getParentFile()) {
                    pathList.add(tmpFile.getParentFile());
                    tmpFile = tmpFile.getParentFile();
                } else {
                    isEnd = true;
                }
            }

            if (0 == pathList.size()) {
                return;
            }

            for (int i = pathList.size() - 1; i >= 0; i--) {
                File path = pathList.get(i);
                if (!path.exists()) {
                    if (!path.mkdir()) {
                        throw new IOException("could not create folder: " + path.getPath());
                    }
                }
            }

            if (!file.exists()) {
                if (!file.createNewFile()) {
                    throw new IOException("could not create new file: " + file.getPath());
                }
            }

            out = new FileOutputStream(file);
            out.write(trimmedData.getBytes());
        } catch (Throwable t) {
            logger.info("FAILED TO WRITE HTML STRING TO FILE: " + file.getPath());
        } finally {
            close(out);
        }
    }


    public static void setEnvironmentVariable(final String varName, final String varValue) {
        Map<String, String> newEnv = new HashMap() {{put(varName, varValue);}};
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newEnv);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newEnv);
        } catch (NoSuchFieldException e) {
            try {
                Class[] classes = Collections.class.getDeclaredClasses();
                Map<String, String> env = System.getenv();
                for (Class cl : classes) {
                    if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                        Field field = cl.getDeclaredField("m");
                        field.setAccessible(true);
                        Object obj = field.get(env);
                        Map<String, String> map = (Map<String, String>) obj;
                        map.clear();
                        map.putAll(newEnv);
                    }
                }
            } catch (Exception e2) {
                logger.error(e2);
            }
        } catch (Exception e1) {
            logger.error(e1);
        }
    }

    public static List<String> getExceptionMessageChain(Throwable throwable) {
        List<String> result = new ArrayList<String>();
        while (throwable != null) {
            result.add(throwable.getMessage());
            throwable = throwable.getCause();
        }
        return result;
    }

    public static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    public static int[] stringArray2intArray(String[] strings)  {
        if (isEmpty(strings)) {
            return new int[0];
        }
        int[] result = new int[strings.length];
        for (int i = 0; i < strings.length;i++) {
            result[i] = Integer.parseInt(strings[i].trim());
        }
        return result;
    }

    public static String arrayToString(final int[] array, String delimiter) {
        return Joiner.on(delimiter).join(Ints.asList(array));
    }

    public static boolean executeCommand(String command, int exTimeout) {
        Process process = null;
        String errString = "";
        try {
            logger.info("ProcImage, Executing external command: '" + command + "'");
            process = Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            logger.info(e.toString());
            return false;
        }

        InputStream inputStream = process.getInputStream();
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
        InputStream errorStream = process.getErrorStream();
        BufferedInputStream bufferedErrorStream = new BufferedInputStream(errorStream);
        boolean ok = false;
        int timeout = exTimeout;
        int exitValue = -999;

        while (!ok) {
            try {
                while (bufferedInputStream.available() > 0 || bufferedErrorStream.available() > 0) {
                    while (bufferedInputStream.available() > 0) {
                        char ch = (char) bufferedInputStream.read();
                        //System.out.print(ch);
                    }

                    while (bufferedErrorStream.available() > 0) {
                        char ch = (char) bufferedErrorStream.read();
                        errString += ch;
                        //System.out.print(ch);
                    }
                }
            } catch (IOException e) {
                logger.info("ProcImage, Couldn't read response");
            }
            try {
                exitValue = process.exitValue();
                ok = true;
            } catch (IllegalThreadStateException e) {
                try {
                    // still running.
                    Thread.sleep(300);
                    timeout = timeout - 300;
                    if (timeout < 0 && timeout >= -300) {
                        logger.info("ALERT: Command doesn't terminate:");
                        logger.info(command);
                        logger.info("Shutting down command...");
                        process.destroy();
                    } else if (timeout < 0) {
                        logger.info("ALERT: Command STILL doesn't terminate:");
                        logger.info(command);
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException e1) {
                    logger.error(e1);
                }
            }
        }
        if (ok) {
            // finished running
            if (exitValue == 0) {
                logger.info("Terminated without errors");
            } else {
                logger.error("Exit code " + exitValue + " while performing command: '" + command + "'");
            }
        } else {
            process.destroy();
        }
        try {
            while (bufferedInputStream.available() > 0) {
                System.out.print((char) bufferedInputStream.read());
            }
            while (bufferedErrorStream.available() > 0) {
                System.out.print((char) bufferedErrorStream.read());
            }
        } catch (IOException e) {
            logger.info("Couldn't read response");
        }
        if (!isEmpty(errString)) {
            logger.error(errString);
        }

        return exitValue == 0;
    }

    public static String getTempDir() {
        //return System.getProperty("user.dir") + File.separator + "temp";
        return getSystemTempDir();
    }


    public static String getCacheDir() {
        return getSystemTempDir() + File.separator + "cache" + File.separator;
//        String tempDir = System.getProperty("user.dir") + File.separator + "temp" + File.separator;
//        if (!tempDir.endsWith(File.separator)) {
//            tempDir = tempDir + File.separator;
//        }
//        return tempDir;
    }


    public static String getSystemTempDir() {
        String tempDir = System.getProperty("java.io.tmpdir");
        if (isEmpty(tempDir)) {
            tempDir = System.getProperty("user.dir") + File.separator + "temp" + File.separator;
        }
        if (!tempDir.endsWith(File.separator)) {
            tempDir = tempDir + File.separator;
        }
        return tempDir;
    }



    public static String generateRandomFilename() {
        StringBuilder fileName = new StringBuilder();
        int randomInt;
        int minValue = 48; // {Digits 0-9 : 48-57}
        int maxValue = 57;

        do {
            randomInt = (int) Math.floor((Math.random() * (maxValue - minValue + 1) + minValue));
            fileName.append((char) randomInt);
            if (fileName.length() == 5 || fileName.length() == 11)
                fileName.append(".");
        } while (fileName.length() < 17);

        return fileName.toString();
    }


    public static void get() {
        List<Integer> numbers = new ArrayList<>();
        for(int i = 0; i < 10; i++){
            numbers.add(i);
        }

        Collections.shuffle(numbers);

        String result = "";
        for(int i = 0; i < 4; i++){
            result += numbers.get(i).toString();
        }
    }


    public static String encodeUrl(String url) {
        if (isEmpty(url)) {
            return "";
        }
        if (url.contains(" ")) {
            url = url.replace(" ", "%20");
        }
        if (url.contains("&")) {
            url = url.replace("&", "%26");
        }

        return url;
    }

    public static String encodeStringToHTML(String inputString) {
        if (isEmpty(inputString)) {
            return "";
        }
        String encodedString = inputString;
        if (inputString.contains("'")) {
            //encodedString = inputString.replace("'", "&#039;");
            encodedString = inputString.replace("'", "’");
        }
        if (inputString.contains("\"")) {
            encodedString = inputString.replace("'", "&quot;");
        }
        if (inputString.contains(">")) {
            encodedString = inputString.replace("'", "&gt;");
        }
        if (inputString.contains("<")) {
            encodedString = inputString.replace("'", "&lt;");
        }
        if (inputString.contains("&")) {
            encodedString = inputString.replace("'", "and");
        }

        return encodedString;
    }



    private static boolean convertSVGwithInkscape(String sourceFileName, String convertedFilename) {
        logger.debug("Converting to PNG with Inkscape");
        String resultString = "";
        try {
            List<String> args = new ArrayList<String>();
            args.add("inkscape");
            args.add("--export-png=" + convertedFilename);
            args.add("--export-dpi=200");
            args.add("--export-background-opacity=0");
            args.add("--without-gui");
            args.add(sourceFileName);
            resultString = run(args);
            if (!resultString.contains("Bitmap saved")) {
                logger.error(resultString);
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            logger.error(e);
            return false;
        }
    }

    public static String convertStreamToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public static Field getPrivateField(Class className, String fieldName) throws NoSuchFieldException {
        Field field = className.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }


    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new LinkedList<>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
            @Override
            public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }



    public static String getEncodedUrl(String urlString) throws MalformedURLException, URISyntaxException {
        if (isEmpty(urlString)) {
            return "";
        }
        String result = "";
        URL url = new URL(urlString);
        try {
            URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            result = uri.toASCIIString();
        } catch (Exception e) {
            result = encodeUrl(urlString);
        }
        return result;
    }

    public static String encodeString(String incomingString) {
        String encodedString = "";
        if (isEmpty(incomingString)) {
            return "";
        }
        try {
            encodedString = URLEncoder.encode(incomingString, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.warn(e.getMessage());
        }
        return encodedString;
    }

    public static String decodeString(String incomingString) {
        String decodedString = "";
        if (isEmpty(incomingString)) {
            return "";
        }
        try {
            decodedString = UriUtils.decode(incomingString, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.warn(e.getMessage());
        }
        return decodedString;
    }


    public static double getJavaVersion () {
        String version = System.getProperty("java.version");
        int pos = version.indexOf('.');
        pos = version.indexOf('.', pos+1);
        return Double.parseDouble (version.substring (0, pos));
    }

    public static String addSlashIfNeed(String str) {
        if (isEmpty(str)) {
            return "/";
        }
        if (!str.endsWith("/")) {
            str = str.concat("/");
        }
        return str;
    }

    static String run(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(cmd);
        StringBuilder sb = new StringBuilder();
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (sb != null) {
                sb.append("\n");
                sb.append(line);
            }
        }
        p.waitFor();
        return sb.toString();
    }

    public static byte[] zip(final String str) {
        if ((str == null) || (str.length() == 0)) {
            throw new IllegalArgumentException("Cannot zip null or empty string");
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
                gzipOutputStream.write(str.getBytes(StandardCharsets.UTF_8));
            }
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to zip content", e);
        }
    }

    public static void w(long msToSleep) {
        try {
            Thread.sleep(msToSleep);
        } catch (InterruptedException e) {
            //logger.warn(e);
            e.printStackTrace();
        }
    }

}