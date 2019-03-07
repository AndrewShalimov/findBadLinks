package org.posts;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.javascript.host.html.HTMLAnchorElement;
import com.gargoylesoftware.htmlunit.util.WebConnectionWrapper;
import net.sourceforge.htmlunit.corejs.javascript.NativeObject;
import org.apache.commons.lang3.StringUtils;
import org.posts.exceptions.GrabberException;
import org.posts.model.Serie;
import org.slf4j.LoggerFactory;
import com.gargoylesoftware.htmlunit.javascript.host.html.HTMLTableCellElement;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.posts.Utils.*;

public class Grabber {

    public final static org.slf4j.Logger logger = LoggerFactory.getLogger(Grabber.class);
    private String newSeriesUrl = "https://www1.swatchseries.to/latest";
    private String swatchSeriesUrl = "https://www1.swatchseries.to/";
    private String openLoadTag = "openload.co";
    private WebClient webClient;
    private HtmlPage page;
    private String openLoadLinkText = "'Delete link ";
    private Pattern openLoadLinkPattern = Pattern.compile(openLoadLinkText + ".*'");

    public Grabber() throws GrabberException {
        try {
            webClient = new WebClient(BrowserVersion.CHROME);
            Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
            webClient.getOptions().setJavaScriptEnabled(true);
            webClient.getOptions().setRedirectEnabled(true);
            webClient.getOptions().setThrowExceptionOnScriptError(false);
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            webClient.getOptions().setCssEnabled(true);
            webClient.getOptions().setUseInsecureSSL(true);
            webClient.waitForBackgroundJavaScript(10000);
            webClient.waitForBackgroundJavaScriptStartingBefore(10000);
            webClient.setAjaxController(new NicelyResynchronizingAjaxController());
            webClient.setJavaScriptTimeout(1000 * 20);
            webClient.setWebConnection(
                    new WebConnectionWrapper(webClient) {
                        public WebResponse getResponse(WebRequest request) throws IOException {
                            //w(500);
                            WebResponse response = super.getResponse(request);
                            String content = response.getContentAsString(Charset.forName("UTF-8"));
                            if (content.contains("429 Too Many Requests")) {
                                w(1000);
                                logger.info("------------ paused");
                                ((HtmlPage)webClient.getCurrentWindow().getEnclosedPage()).refresh();
                            }
                            String jQueryScriptSuffix = "jquery-1.10.2.min.js\"></script>";
                            if (content.contains(jQueryScriptSuffix)) {
                                String extentJqueryToSearchCaseInsensitive = " \n<script>$.expr[\":\"].contains = $.expr.createPseudo(function(arg) { " +
                                        "    return function( elem ) { " +
                                        "        return $(elem).text().toUpperCase().indexOf(arg.toUpperCase()) >= 0; " +
                                        "    }; " +
                                        "});</script> \n";
                                content = content.replace(jQueryScriptSuffix, jQueryScriptSuffix + extentJqueryToSearchCaseInsensitive);
                            }

                            WebResponseData data;
                            //change content
                            if (content.contains("function isValidIdentity(variable)")) {
                                content = content.replace("function isValidIdentity(variable)", "function isValidIdentity(variable) {return true;} \r\n function isValidIdentity_old(variable)");
                            }
                            if (content.contains("\"use strict\";")) {
                                content = content.replace("\"use strict\";", "");
                            }
                            if ("gzip".equalsIgnoreCase(response.getResponseHeaderValue("Content-Encoding"))
                                    && !isEmpty(content)) {
                                data = new WebResponseData(zip(content), response.getStatusCode(), response.getStatusMessage(), response.getResponseHeaders());
                            } else {
                                data = new WebResponseData(content.getBytes("UTF-8"), response.getStatusCode(), response.getStatusMessage(), response.getResponseHeaders());
                            }
                            response = new WebResponse(data, request, response.getLoadTime());
                            return response;
                        }
                    });

        } catch (Exception e) {
            throw new GrabberException(e);
        }
        validateSwatchSeriesHost();
    }


    private void validateSwatchSeriesHost() throws GrabberException {
        try {
            page = webClient.getPage(swatchSeriesUrl);
            if (page.getElementById("s") == null) { // trying to work with HTTP
                newSeriesUrl = "http://www1.swatchseries.to/latest";
                swatchSeriesUrl = "http://www1.swatchseries.to/";
            }
        } catch (Exception e) {
            logger.error("Unable to wok with '{}': {}", swatchSeriesUrl, getStackTrace(e));
            throw new GrabberException(e);
        }
    }

    public List<Serie> grabNewSeries() {
        return grabNewSeries(new ArrayList<Serie>());
    }

    private Serie grabOpenLoadLinksForSerie(Serie serie) {
        try {
            page = webClient.getPage(swatchSeriesUrl);
            w(1000);
            HtmlInput searchInput = (HtmlInput) page.getElementById("s");
            logger.info("looking for '{}'", serie.getFullName());
            searchInput = (HtmlInput) page.getElementById("s");
            searchInput.setValueAttribute(serie.getName());
            HtmlButtonInput searchButton = (HtmlButtonInput) page.getElementById("site-search-submit");
            searchButton.click();
            page = (HtmlPage) webClient.getCurrentWindow().getEnclosedPage();

            int resultSize = page.getByXPath("//div[contains(@class, 'search-item clearfix')]").size();
            if (resultSize == 0) {
                logger.warn("'{}' wasn't found.", serie.getFullName());
                return serie;
            }
            try {
                //String jQuerySelector = "$(\"a:contains('" + serie.getName() + "')\")";
                String jQuerySelector = "$(\"a:contains('" + serie.getName() + "'):contains('" + serie.getName() + " (')\")";
                ScriptResult jsResult = page.executeJavaScript(jQuerySelector);
                int foundSerialsCount = 0;
                try {
                    foundSerialsCount = new Float(((NativeObject) jsResult.getJavaScriptResult()).get("length").toString()).intValue();
                } catch (NumberFormatException e) {
                    logger.warn(e.getMessage());
                }
                if (foundSerialsCount == 0) {
                    String encodedSerieName = encodeStringToHTML(serie.getName());
                    jQuerySelector = "$(\"a:contains('" + encodedSerieName + "'):contains('" + encodedSerieName + " (')\")";
                    jsResult = page.executeJavaScript(jQuerySelector);
                    foundSerialsCount = new Float(((NativeObject) jsResult.getJavaScriptResult()).get("length").toString()).intValue();
                }
                if (foundSerialsCount == 0) {
                    logger.warn("'{}' wasn't found.", serie.getFullName());
                    return serie;
                }
                String serieLink = ((HTMLAnchorElement) ((NativeObject) jsResult.getJavaScriptResult()).get(0)).getHref();
                if (foundSerialsCount > 1) {
                    TreeMap<Integer, String> suitableSeriesLinks = new TreeMap<>(Comparator.reverseOrder());
                    Pattern serieNamePattern = Pattern.compile("(\\d{4})");
                    serieLink = ((HTMLAnchorElement) ((NativeObject) jsResult.getJavaScriptResult()).get(0)).getHref();
                    try {
                        for (int i = 0; i < foundSerialsCount; i++) {
                            String link = ((HTMLAnchorElement) ((NativeObject) jsResult.getJavaScriptResult()).get(i)).getHref();
                            String innerText = ((HTMLAnchorElement) ((NativeObject) jsResult.getJavaScriptResult()).get(i)).getInnerText();
                            Matcher matcher = serieNamePattern.matcher(innerText);
                            matcher.find();
                            Integer year = new Integer(matcher.group().replace("_", ""));
                            suitableSeriesLinks.put(year, link);
                        }
                        serieLink = suitableSeriesLinks.firstEntry().getValue();
                    } catch (Exception e) {
                        logger.warn(e.getMessage());
                    }

                }

                page = webClient.getPage(serieLink);
                String jQueryDetailsSelector = "$($(\"span:contains('Season " + serie.getSeason() + "')\").parent().parent().parent()).find(\"Span:contains('Episode " + serie.getEpisode() + "')\").parent()";
                jsResult = tryToFind(page, jQueryDetailsSelector);
                if (jsResult == null) {
                    String seasonNumber = new Integer(serie.getSeason()).toString();
                    jQueryDetailsSelector = "$($(\"span:contains('Season " + seasonNumber + "')\").parent().parent().parent()).find(\"Span:contains('Episode " + serie.getEpisode() + "')\").parent()";
                    jsResult = tryToFind(page, jQueryDetailsSelector);
                    if (jsResult == null) {
                        String episode = new Integer(serie.getEpisode()).toString();
                        jQueryDetailsSelector = "$($(\"span:contains('Season " + seasonNumber + "')\").parent().parent().parent()).find(\"Span:contains('Episode " + episode + "')\").parent()";
                        jsResult = tryToFind(page, jQueryDetailsSelector);
                        if (jsResult == null) {
                            logger.warn("'{}' wasn't found.", serie.getFullName());
                            return serie;
                        }
                    }
                }

                serieLink = getLinkToSeason(jsResult, serie.getTitle());
                //serieLink = ((HTMLAnchorElement) ((NativeObject) jsResult.getJavaScriptResult()).get(0)).getHref();
                page = webClient.getPage(serieLink);

                jsResult = page.executeJavaScript("$(\"tr[class*='openload'] td.deletelinks\")");
                NativeObject filteredOpenLoadLinks = (NativeObject) jsResult.getJavaScriptResult();
                for (int i = 0; i < new Double((Double) filteredOpenLoadLinks.get("length")).intValue(); i++) {
                    HTMLTableCellElement ht = (HTMLTableCellElement) filteredOpenLoadLinks.get(i);
                    String innerHtml = ht.getInnerHTML();
                    Matcher matcher = openLoadLinkPattern.matcher(innerHtml);
                    matcher.find();
                    String finalOpenLoadLink = matcher.group();
                    finalOpenLoadLink = finalOpenLoadLink.replace(openLoadLinkText, "");
                    finalOpenLoadLink = finalOpenLoadLink.substring(0, finalOpenLoadLink.length() - 1);
                    serie.getOpenLoadLinks().add(new URL(finalOpenLoadLink));
                }
                if (serie.getOpenLoadLinks().size() > 0) {
                    logger.info("'{}' processed successfully.", serie.getFullName());
                } else {
                    logger.warn("Unable to get OpenLoad link for '{}'", serie.getFullName());
                }

            } catch (Exception e) {
                logger.warn("Unable to get OpenLoad link: " + Utils.getStackTrace(e));
            }
        } catch (Exception e) {
            logger.warn("Unable to get 'www1.swatchseries.to' host: " + Utils.getStackTrace(e));
        }
        return serie;
    }


    private String getLinkToSeason(ScriptResult jsResult, String episodeName) {
        String resultLink = "";
        try {
            int linksCount =  new Float(((NativeObject) jsResult.getJavaScriptResult()).get("length").toString()).intValue();
            for (int i = 0; i < linksCount; i++) {
                String anchorContent = ((HTMLAnchorElement) ((NativeObject) jsResult.getJavaScriptResult()).get(i)).getInnerHTML();
                if (StringUtils.containsIgnoreCase(anchorContent, episodeName)) {
                    resultLink = ((HTMLAnchorElement) ((NativeObject) jsResult.getJavaScriptResult()).get(i)).getHref();
                    break;
                }
            }
        } catch (NumberFormatException e) {
            logger.warn(e.getMessage());
        }
        return resultLink;
    }

    private ScriptResult tryToFind(HtmlPage page, String jQuerySelector) {
        ScriptResult jsResult = null;
        try {
            jsResult = page.executeJavaScript(jQuerySelector);
            int foundSerialsCount = new Float(((NativeObject) jsResult.getJavaScriptResult()).get("length").toString()).intValue();
            if (foundSerialsCount == 0) {
                return null;
            }
        } catch (Exception e) {
            logger.warn(e.getMessage());
            return null;
        }
        return jsResult;
    }


    public List<Serie> grabNewSeries(List<Serie> seriesToFind) {
        for (Serie serie : seriesToFind) {
            serie = grabOpenLoadLinksForSerie(serie);
        }
        return seriesToFind;
    }

    public List<Serie> grabNewSeries_old(List<Serie> targetSeries) {
        List<Serie> series = new ArrayList();
        try {
            Pattern serieNamePattern = Pattern.compile(".* (?=- Season)");
            page = webClient.getPage(newSeriesUrl);
            List<HtmlListItem> elements = new ArrayList();
            elements.addAll(page.getByXPath("//*[@id=\"left\"]/ul/li"));
            elements.addAll(page.getByXPath("//*[@id=\"right\"]/ul/li"));

            for (HtmlListItem li : elements) {
                HtmlAnchor link = (HtmlAnchor) li.getFirstChild();
                String href = ((HtmlAnchor) li.getFirstChild()).getHrefAttribute();
                String title = link.getAttribute("title");
                Matcher matcher = serieNamePattern.matcher(title);
                matcher.find();
                String name = matcher.group().trim();
                String date = link.getLastChild().asText();
                //String name = li.getFirstChild().getFirstChild().getNextSibling().getNextSibling().getFirstChild().asText();
                String number = li.getFirstChild().getFirstChild().getTextContent();
                String season = number.split("x")[0];
                String episode = number.split("x")[1];
                Serie serie = new Serie(name, season, episode, title, href, date);
                series.add(serie);
            }
            //extract OpenLoad links from next page
            if (!isEmpty(targetSeries)) {
                series = filterSeries(series, targetSeries);
            }
            for (Serie serie : series) {
                try {
                    page = webClient.getPage(serie.getsWatchSeriesLink());
                    ScriptResult result = page.executeJavaScript("$(\"tr[class*='openload'] td.deletelinks\")");
                    NativeObject sb = (NativeObject) result.getJavaScriptResult();
                    for (int i = 0; i < new Double((Double) sb.get("length")).intValue(); i++) {
                        HTMLTableCellElement ht = (HTMLTableCellElement) sb.get(i);
                        String innerHtml = ht.getInnerHTML();
                        Matcher matcher = openLoadLinkPattern.matcher(innerHtml);
                        matcher.find();
                        String finalOpenLoadLink = matcher.group();
                        finalOpenLoadLink = finalOpenLoadLink.replace(openLoadLinkText, "");
                        finalOpenLoadLink = finalOpenLoadLink.substring(0, finalOpenLoadLink.length() - 1);
                        serie.getOpenLoadLinks().add(new URL(finalOpenLoadLink));
                    }
                } catch (Exception e) {
                    logger.warn("Unable to get OpenLoad link: " + Utils.getStackTrace(e));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return series;
    }

    private List<Serie> filterSeries(List<Serie> allSeries, List<Serie> targetSeries) {
//        List<Serie> filteredSeries =
//                allSeries.stream()
//                        .filter(s -> targetSeries.stream().map(Serie::getName).anyMatch(name -> name.equals(s.getName())))
//                        .filter(s -> targetSeries.stream().map(Serie::getSeason).anyMatch(season -> season.equals(s.getSeason())))
//                        .filter(s -> targetSeries.stream().map(Serie::getEpisode).anyMatch(episode -> episode.equals(s.getEpisode())))
//                        .collect(Collectors.toList());
        List<Serie> filteredSeries = new ArrayList<>();
        for (Serie serie : allSeries) {
            for (Serie targetSerie : targetSeries) {
                if (serie.getName().equalsIgnoreCase(targetSerie.getName())
                        && serie.getSeason().equals(targetSerie.getSeason())
                        && serie.getEpisode().equals(targetSerie.getEpisode())
                        && !isEmpty(targetSerie.getPostId())) {

                    serie.setPostId(targetSerie.getPostId());
                    filteredSeries.add(serie);
                    break;
                }
            }
        }
        return filteredSeries;
    }

}
