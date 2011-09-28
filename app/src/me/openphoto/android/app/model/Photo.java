
package me.openphoto.android.app.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class representing a Photo on OpenPhoto.
 * 
 * @author Patrick Boos
 */
public class Photo {
    public static final int PERMISSION_PUBLIC = 1;
    public static final int PERMISSION_PRIVATE = 0;

    private String mId;
    private final List<String> mTags;
    private String mAppId;
    private final Map<String, String> mUrls;
    private String mTitle;
    private String mDescription;
    private int mPermission;

    /**
     * Constructor which probably will not be used externally. Everything should
     * be done through fromJson().
     */
    private Photo() {
        mTags = new ArrayList<String>();
        mUrls = new HashMap<String, String>();
    }

    /**
     * Creates a Photo object from json.
     * 
     * @param json JSONObject of the Photo as received from the OpenPhoto API
     * @return Photo as represented in the given json
     * @throws JSONException
     */
    public static Photo fromJson(JSONObject json) throws JSONException {
        Photo photo = new Photo();
        photo.mId = json.optString("id");
        photo.mAppId = json.optString("appId");
        photo.mTitle = json.optString("title");
        photo.mDescription = json.optString("description");
        photo.mPermission = json.optInt("permission", PERMISSION_PRIVATE);

        String host = "http://" + json.optString("host");
        String base = json.optString("pathBase");
        String original = json.optString("pathOriginal");
        photo.mUrls.put("base", host + base);
        photo.mUrls.put("original", host + original);

        // Tags
        JSONArray tags = json.getJSONArray("tags");
        for (int i = 0; i < tags.length(); i++) {
            photo.mTags.add(tags.getString(i));
        }

        // Urls
        for (int i = 0; i < json.names().length(); i++) {
            String name = json.names().optString(i);
            if (name.startsWith("path") && name.charAt(4) >= '0' && name.charAt(4) <= '9') {
                photo.mUrls.put(name.substring(4), json.getString(name));
            }
        }

        // TODO: pathBase, exifCameraMake, height, creativeCommons,
        // dateUploaded, dateUploadedDay,dateUploadedMonth, dateUploadedYear,
        // latitude, longitude, host, hash, status, width, dateTaken,
        // dateTakenDay, dateTakenMonth, dateTakenYear, permission,
        // pathOriginal, size, views, exifCameraModel
        return photo;
    }

    /**
     * @return id
     */
    public String getId() {
        return mId;
    }

    /**
     * Tags associated with this Photo.
     * 
     * @return list of tags
     */
    public List<String> getTags() {
        return mTags;
    }

    /**
     * Returns the AppId of the server on which this Photo is located.
     * 
     * @return AppId
     */
    public String getAppId() {
        return mAppId;
    }

    /**
     * Get URL for a specific size. Examples are: 50x50xCR and 640x960 . CR
     * stands for cropped.
     * 
     * @param size size of the pictures
     * @return Url for a picture of the given size
     */
    public String getUrl(String size) {
        return mUrls.get(size);
    }

    /**
     * @return title of the photo
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * @return description of the photo
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * @return true if the picture is private
     */
    public boolean isPrivate() {
        return mPermission == Photo.PERMISSION_PRIVATE;
    }
}