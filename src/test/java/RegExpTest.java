import com.afrozaar.wordpress.wpapi.v2.Wordpress;
import com.afrozaar.wordpress.wpapi.v2.model.Post;
import com.afrozaar.wordpress.wpapi.v2.request.Request;
import com.afrozaar.wordpress.wpapi.v2.request.SearchRequest;
import com.afrozaar.wordpress.wpapi.v2.response.PagedResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.posts.PostAnalyser;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegExpTest {

    @Test
    public void searchEncodedFiles() throws Exception {

        String text = "D:\\tools\\jMeter\\apache-jmeter-4.0\\bin>cd d:\\projects\\Korzh\\blink-authoring-tools\\ d:\\projects\\Korzh\\blink-authoring-tools>python main.py acd51e4d-0083-46e4-92cc-fc4368c56ca4{'data': {'createGameUpload': {'id': 'ecf8e1c0-6485-4799-beef-22d92dcaea49', 'intervalsToUpload': [{'inMs': 83635, 'outMs': 116569}]}}}{'data': {'createGameUpload': {'id': 'ecf8e1c0-6485-4799-beef-22d92dcaea49', 'intervalsToUpload': [{'inMs': 83635, 'outMs': 116569}]}}}{'data': {'keepVideo': '[object Object]'}}{'data': {'endSession': 'Session submited for processing.'}}{'data': {'blink': {'id': 'acd51e4d-0083-46e4-92cc-fc4368c56ca4', 'status': 'PROCESSING'}}}{'data': {'blink': {'id': 'acd51e4d-0083-46e4-92cc-fc4368c56ca4', 'status': 'READY'}}}{'data': {'blink': {'id': 'acd51e4d-0083-46e4-92cc-fc4368c56ca4', 'status': 'READY'}}}{'data': {'updateBlink': {'id': 'acd51e4d-0083-46e4-92cc-fc4368c56ca4'}}}{'data': {'blink': {'id': 'acd51e4d-0083-46e4-92cc-fc4368c56ca4', 'status': 'READY'}}}{'data': {'updateBlinkStatus': {'id': 'acd51e4d-0083-46e4-92cc-fc4368c56ca4'}}}d:\\projects\\Korzh\\blink-authoring-tools>rem";
        String strPattern = "({'data': {'updateBlinkStatus': {'id': ')(.*)('}}})";

//        Pattern pattern = Pattern.compile(strPattern);
//        Matcher matcher = pattern.matcher(text);
//        matcher.find();


        Pattern pattern = Pattern.compile("python main.py (.*?)((\\{\\'data\\')|( * ))");
        Matcher matcher = pattern.matcher(text);
        matcher.find();
        String found = matcher.group(1);
        System.out.println(found);

        text = "D:\\tools\\jMeter\\apache-jmeter-4.0\\bin>cd d:\\\\projects\\\\Korzh\\\\blink-authoring-tools\\\\ d:\\projects\\Korzh\\blink-authoring-tools>python main.py 11dc9462-fb2f-4d4b-95a5-3c1b5ffe775d * Serving Flask app \"Auth0 login\" (lazy loading) * Environment: production   WARNING: Do not use the development server in a production environment.   Use a production WSGI server instead. * Debug mode: off{'data': {'createGameUpload': {'id': 'da74806b-643a-40df-951f-8ee84a6d582b', 'intervalsToUpload': [{'inMs': 83635, 'outMs': 116569}]}}}{'data': {'createGameUpload': {'id': 'da74806b-643a-40df-951f-8ee84a6d582b', 'intervalsToUpload': [{'inMs': 83635, 'outMs': 116569}]}}}{'data': {'keepVideo': '[object Object]'}}{'data': {'endSession': 'Session submited for processing.'}}{'data': {'blink': {'id': '11dc9462-fb2f-4d4b-95a5-3c1b5ffe775d', 'status': 'PROCESSING'}}}{'data': {'blink': {'id': '11dc9462-fb2f-4d4b-95a5-3c1b5ffe775d', 'status': 'READY'}}}{'data': {'blink': {'id': '11dc9462-fb2f-4d4b-95a5-3c1b5ffe775d', 'status': 'READY'}}}{'data': {'updateBlink': {'id': '11dc9462-fb2f-4d4b-95a5-3c1b5ffe775d'}}}{'data': {'blink': {'id': '11dc9462-fb2f-4d4b-95a5-3c1b5ffe775d', 'status': 'READY'}}}{'data': {'updateBlinkStatus': {'id': '11dc9462-fb2f-4d4b-95a5-3c1b5ffe775d'}}}d:\\projects\\Korzh\\blink-authoring-tools>rem\n" +
                "11dc9462-fb2f-4d4b-95a5-3c1b5ffe775d * Serving Flask app \"Auth0 login\" (lazy loading) * Environment: production   WARNING: Do not use the development server in a production environment.   Use a production WSGI server instead. * Debug mode: off";
        pattern = Pattern.compile("python main.py (.*?)((\\{\\'data\\')|( * ))");
        matcher = pattern.matcher(text);
        matcher.find();
        found = matcher.group(1);
        System.out.println(found);
    }
}
