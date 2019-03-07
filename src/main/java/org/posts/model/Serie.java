package org.posts.model;

import org.posts.Grabber;

import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class Serie {
    private SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd");
    private String name;
    private String season;
    private String episode;
    private String title;
    private Date date = new Date();
    private List<URL> openLoadLinks = new ArrayList();
    private String sWatchSeriesLink;
    private String postId;
    private Boolean successUpdate = false;
    private String errorMessage = "";

    public Serie(String name, String season, String episode, String title, String href, String date) {
        this.name = name;
        this.title = title;
        this.season = season;
        this.episode = episode;
        this.sWatchSeriesLink = href;
        this.date = parseDate(date);
    }

    @Override
    public String toString() {
        return "Serie {" +
                "name='" + name + '\'' +
                " season='" + season + '\'' +
                " episode='" + episode + '\'' +
                ", title='" + title + '\'' +
                ", date=" + sdf.format(date) +
                ", openLoadLinks=" + openLoadLinks +
                ", sWatchSeriesLink='" + sWatchSeriesLink + '\'' +
                ", postId='" + postId + '\'' +
                '}';
    }

    public Serie() {
    }

    public Serie(String title, String href, String date) {
        this.title = title;
        this.sWatchSeriesLink = href;
        this.date = parseDate(date);
    }

    public Serie(String season, String episode, String title, String href, String date) {
        this.title = title;
        this.season = season;
        this.episode = episode;
        this.sWatchSeriesLink = href;
        this.date = parseDate(date);
    }

    private Date parseDate(String strDate) {
        Date date = new Date();
        try {
            date = sdf.parse(strDate);
        } catch (ParseException e) {
            Grabber.logger.warn("Unable to get date for Serie '{}'. Skip it.", title);
        }
        return date;
    }


    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public List<URL> getOpenLoadLinks() {
        return openLoadLinks;
    }

    public void setOpenLoadLinks(List<URL> openLoadLinks) {
        this.openLoadLinks = openLoadLinks;
    }

    public String getsWatchSeriesLink() {
        return sWatchSeriesLink;
    }

    public void setsWatchSeriesLink(String sWatchSeriesLink) {
        this.sWatchSeriesLink = sWatchSeriesLink;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public String getEpisode() {
        return episode;
    }

    public void setEpisode(String episode) {
        this.episode = episode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public Boolean getSuccessUpdate() {
        return successUpdate;
    }

    public void setSuccessUpdate(Boolean successUpdate) {
        this.successUpdate = successUpdate;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getFullName() {
        return this.name + " S" + this.season + "E" + this.episode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Serie serie = (Serie) o;
        return Objects.equals(name, serie.name) &&
                Objects.equals(season, serie.season) &&
                Objects.equals(episode, serie.episode) &&
                Objects.equals(title, serie.title) &&
                Objects.equals(date, serie.date) &&
                Objects.equals(openLoadLinks, serie.openLoadLinks) &&
                Objects.equals(sWatchSeriesLink, serie.sWatchSeriesLink) &&
                Objects.equals(postId, serie.postId) &&
                Objects.equals(successUpdate, serie.successUpdate) &&
                Objects.equals(errorMessage, serie.errorMessage);
    }

    @Override
    public int hashCode() {

        return Objects.hash(name, season, episode, title, date, openLoadLinks, sWatchSeriesLink, postId, successUpdate, errorMessage);
    }
}