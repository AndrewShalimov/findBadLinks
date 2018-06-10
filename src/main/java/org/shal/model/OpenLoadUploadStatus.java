package org.shal.model;

import java.util.Objects;

public class OpenLoadUploadStatus {
    public String id;
    public String remoteurl;
    public String status;
    public String bytes_loaded;
    public String bytes_total;
    public String folderid;
    public String added;
    public String last_update;
    public String extid;
    public String url;

    public OpenLoadUploadStatus() {
    }

    public OpenLoadUploadStatus(String id, String remoteurl, String status, String bytes_loaded, String bytes_total, String folderid, String added, String last_update, String extid, String url) {
        this.id = id;
        this.remoteurl = remoteurl;
        this.status = status;
        this.bytes_loaded = bytes_loaded;
        this.bytes_total = bytes_total;
        this.folderid = folderid;
        this.added = added;
        this.last_update = last_update;
        this.extid = extid;
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenLoadUploadStatus that = (OpenLoadUploadStatus) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(remoteurl, that.remoteurl) &&
                Objects.equals(status, that.status) &&
                Objects.equals(bytes_loaded, that.bytes_loaded) &&
                Objects.equals(bytes_total, that.bytes_total) &&
                Objects.equals(folderid, that.folderid) &&
                Objects.equals(added, that.added) &&
                Objects.equals(last_update, that.last_update) &&
                Objects.equals(extid, that.extid) &&
                Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id, remoteurl, status, bytes_loaded, bytes_total, folderid, added, last_update, extid, url);
    }

    @Override
    public String toString() {
        return "OpenLoadUploadStatus{" +
                "id='" + id + '\'' +
                ", remoteurl='" + remoteurl + '\'' +
                ", status='" + status + '\'' +
                ", bytes_loaded='" + bytes_loaded + '\'' +
                ", bytes_total='" + bytes_total + '\'' +
                ", folderid='" + folderid + '\'' +
                ", added='" + added + '\'' +
                ", last_update='" + last_update + '\'' +
                ", extid='" + extid + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
