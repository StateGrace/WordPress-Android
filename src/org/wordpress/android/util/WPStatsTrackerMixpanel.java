package org.wordpress.android.util;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONException;
import org.json.JSONObject;
import org.wordpress.android.Config;
import org.wordpress.android.WordPress;

import java.util.EnumMap;
import java.util.Iterator;

public class WPStatsTrackerMixpanel implements WPStats.Tracker {

    private MixpanelAPI mMixpanel;
    private EnumMap<WPStats.Stat,JSONObject> aggregatedProperties;

    public WPStatsTrackerMixpanel(){
        aggregatedProperties = new EnumMap<WPStats.Stat, JSONObject>(WPStats.Stat.class);
    }

    @Override
    public void track(WPStats.Stat stat) {
        track(stat, null);
    }

    @Override
    public void track(WPStats.Stat stat, JSONObject properties) {
        WPStatsTrackerMixpanelInstructionsForStat instructions = instructionsForStat(stat);

        if (instructions == null)
            return;

        trackMixpanelDataForInstructions(instructions, properties);
    }

    private void trackMixpanelDataForInstructions(WPStatsTrackerMixpanelInstructionsForStat instructions, JSONObject properties) {
        if (instructions.getDisableForSelfHosted()) {
            return;
        }

        String eventName = instructions.getMixpanelEventName();
        if (eventName != null && !eventName.isEmpty()) {
            JSONObject savedPropertiesForStat = propertiesForStat(instructions.getStat());
            if (savedPropertiesForStat == null) {
                savedPropertiesForStat = new JSONObject();
            }

            // Retrieve properties user has already passed in and combine them with the saved properties
            if (properties != null) {
                Iterator<String> iter = properties.keys();
                while (iter.hasNext()) {
                    String key = iter.next();
                    try {
                        Object value = properties.get(key);
                        savedPropertiesForStat.put(key, value);
                    } catch (JSONException e) {
                        AppLog.e(AppLog.T.UTILS, e);
                    }
                }
            }
            mMixpanel.track(eventName, savedPropertiesForStat);
            removePropertiesForStat(instructions.getStat());
        }

        if (instructions.getPeoplePropertyToIncrement() != null && !instructions.getPeoplePropertyToIncrement().isEmpty())
            incrementPeopleProperty(instructions.getPeoplePropertyToIncrement());

        if (instructions.getSuperPropertyToIncrement() != null && !instructions.getSuperPropertyToIncrement().isEmpty())
            incrementSuperProperty(instructions.getSuperPropertyToIncrement());

        if (instructions.getPropertyToIncrement() != null && !instructions.getPropertyToIncrement().isEmpty())
            incrementProperty(instructions.getPropertyToIncrement(), instructions.getStatToAttachProperty());

        if (instructions.getSuperPropertyToFlag() != null && !instructions.getSuperPropertyToFlag().isEmpty())
            flagSuperProperty(instructions.getSuperPropertyToFlag());
    }

    @Override
    public void beginSession() {
        mMixpanel = MixpanelAPI.getInstance(WordPress.getContext(), Config.MIXPANEL_TOKEN);

        // Tracking session count will help us isolate users who just installed the app
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(WordPress.getContext());
        int sessionCount = preferences.getInt("sessionCount", 0);
        sessionCount++;
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("sessionCount", sessionCount);
        editor.commit();

        // Register super properties
        boolean connected = WordPress.hasValidWPComCredentials(WordPress.getContext());
        int numBlogs = WordPress.wpDB.getVisibleAccounts().size();
        try {
            JSONObject properties = new JSONObject();
            properties.put("platform", "Android");
            properties.put("session_count", sessionCount);
            properties.put("connected_to_dotcom", connected);
            properties.put("number_of_blogs", numBlogs);
            mMixpanel.registerSuperProperties(properties);
        } catch(JSONException e) {
            AppLog.e(AppLog.T.UTILS, e);
        }

        // Application opened and start.
        if (connected) {
            String username = preferences.getString(WordPress.WPCOM_USERNAME_PREFERENCE, null);
            mMixpanel.identify(username);
            mMixpanel.getPeople().identify(username);
            mMixpanel.getPeople().increment("Application Opened", 1);

            try {
                JSONObject jsonObj = new JSONObject();
                jsonObj.put("$username", username);
                jsonObj.put("$first_name", username);
                mMixpanel.getPeople().set(jsonObj);
            } catch (JSONException e) {
                AppLog.e(AppLog.T.UTILS, e);
            }
        }
    }

    @Override
    public void endSession() {
        aggregatedProperties.clear();
        mMixpanel.flush();
    }

    @Override
    public void clearAllData() {
        endSession();
        mMixpanel.clearSuperProperties();
        mMixpanel.getPeople().clearPushRegistrationId();
    }

    private WPStatsTrackerMixpanelInstructionsForStat instructionsForStat(WPStats.Stat stat)
    {
        WPStatsTrackerMixpanelInstructionsForStat instructions = null;
        switch (stat) {
            case APPLICATION_OPENED:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Application Opened");
                break;
            case APPLICATION_CLOSED:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Application Closed");
                break;
            case THEMES_ACCESSED_THEMES_BROWSER:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Themes - Accessed Theme Browser");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_accessed_theme_browser");
                break;
            case THEMES_CHANGED_THEME:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Themes - Changed Theme");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_changed_theme");
                break;
            case READER_ACCESSED:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Reader - Accessed");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_accessed_reader");
                break;
            case READER_OPENED_ARTICLE:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Reader - Opened Article");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_opened_article");
                break;
            case READER_LIKED_ARTICLE:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Reader - Liked Article");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_liked_article");
                break;
            case READER_REBLOGGED_ARTICLE:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Reader - Reblogged Article");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_reblogged_article");
                break;
            case READER_INFINITE_SCROLL:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Reader - Infinite Scroll");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_reader_performed_infinite_scroll");
                break;
            case READER_FOLLOWED_READER_TAG:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Reader - Followed Reader Tag");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_followed_reader_tag");
                break;
            case READER_UNFOLLOWED_READER_TAG:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Reader - Unfollowed Reader Tag");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_unfollowed_reader_tag");
                break;
            case READER_LOADED_TAG:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Reader - Loaded Tag");
                break;
            case READER_LOADED_FRESHLY_PRESSED:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Reader - Loaded Freshly Pressed");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_loaded_freshly_pressed");
                break;
            case READER_COMMENTED_ON_ARTICLE:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Reader - Commented on Article");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_commented_on_article");
                break;
            case EDITOR_CREATED_POST:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Editor - Created Post");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_editor_created_post");
                break;
           case EDITOR_ADDED_PHOTO_VIA_LOCAL_LIBRARY:
               instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Editor - Added Photo via Local Library");
               instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_added_photo_via_local_library");
               break;
           case EDITOR_ADDED_PHOTO_VIA_WP_MEDIA_LIBRARY:
               instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Editor - Added Photo via WP Media Library");
               instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_added_photo_via_wp_media_library");
               break;
           case EDITOR_PUBLISHED_POST:
               instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Editor - Published Post");
               instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_editor_published_post");
               break;
          case EDITOR_UPDATED_POST:
              instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Editor - Updated Post");
              instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_editor_updated_post");
              break;
          case EDITOR_PUBLISHED_POST_WITH_PHOTO:
              instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithSuperPropertyAndPeoplePropertyIncrementor("number_of_published_posts_with_photos");
              break;
          case EDITOR_PUBLISHED_POST_WITH_VIDEO:
              instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithSuperPropertyAndPeoplePropertyIncrementor("number_of_published_posts_with_videos");
              break;
          case EDITOR_PUBLISHED_POST_WITH_CATEGORIES:
              instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithSuperPropertyAndPeoplePropertyIncrementor("number_of_published_posts_with_categories");
              break;
          case EDITOR_PUBLISHED_POST_WITH_TAGS:
              instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithSuperPropertyAndPeoplePropertyIncrementor("number_of_published_posts_with_tags");
              break;
            case NOTIFICATIONS_ACCESSED:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Notifications - Accessed");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_accessed_notifications");
                break;
            case NOTIFICATIONS_OPENED_NOTIFICATION_DETAILS:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Notifications - Opened Notification Details");
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_opened_notification_details");
                break;
            case NOTIFICATION_PERFORMED_ACTION:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithSuperPropertyAndPeoplePropertyIncrementor("number_of_times_notifications_performed_action_against");
                break;
            case NOTIFICATION_APPROVED:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithSuperPropertyAndPeoplePropertyIncrementor("number_of_times_notifications_approved");
                break;
            case NOTIFICATION_REPLIED_TO:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithSuperPropertyAndPeoplePropertyIncrementor("number_of_times_notifications_replied_to");
                break;
            case NOTIFICATION_TRASHED:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithSuperPropertyAndPeoplePropertyIncrementor("number_of_times_notifications_trashed");
                break;
            case NOTIFICATION_FLAGGED_AS_SPAM:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithSuperPropertyAndPeoplePropertyIncrementor("number_of_times_notifications_flagged_as_spam");
                break;
            case OPENED_POSTS:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithPropertyIncrementor("number_of_times_opened_posts", WPStats.Stat.APPLICATION_CLOSED);
                break;
            case OPENED_PAGES:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithPropertyIncrementor("number_of_times_opened_pages", WPStats.Stat.APPLICATION_CLOSED);
                break;
            case OPENED_COMMENTS:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithPropertyIncrementor("number_of_times_opened_comments", WPStats.Stat.APPLICATION_CLOSED);
                break;
            case OPENED_VIEW_SITE:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithPropertyIncrementor("number_of_times_opened_view_site", WPStats.Stat.APPLICATION_CLOSED);
                break;
            case OPENED_VIEW_ADMIN:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithPropertyIncrementor("number_of_times_opened_view_admin", WPStats.Stat.APPLICATION_CLOSED);
                instructions.setSuperPropertyAndPeoplePropertyToIncrement("number_of_times_opened_view_admin");
                break;
            case OPENED_MEDIA_LIBRARY:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithPropertyIncrementor("number_of_times_opened_media_library", WPStats.Stat.APPLICATION_CLOSED);
                break;
            case OPENED_SETTINGS:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithPropertyIncrementor("number_of_times_opened_settings", WPStats.Stat.APPLICATION_CLOSED);
                break;
            case CREATED_ACCOUNT:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Created Account");
                break;
            case CREATED_SITE:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsForEventName("Created Site");
                break;
            case SHARED_ITEM:
                instructions = WPStatsTrackerMixpanelInstructionsForStat.mixpanelInstructionsWithSuperPropertyAndPeoplePropertyIncrementor("number_of_items_share");
                break;
        }
        return instructions;
    }

    private void incrementPeopleProperty(String property) {
        mMixpanel.getPeople().increment(property, 1);
    }

    private void incrementSuperProperty(String property) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(WordPress.getContext()) ;
        int propertyCount = preferences.getInt(property, 0);
        propertyCount++;
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(property, propertyCount);
        editor.commit();

        try {
            JSONObject superProperty = new JSONObject();
            superProperty.put(property, propertyCount);
            mMixpanel.registerSuperProperties(superProperty);
        } catch (JSONException e) {
            AppLog.e(AppLog.T.UTILS, e);
        }
    }

    private void flagSuperProperty(String property) {
        try {
            JSONObject superProperty = new JSONObject();
            superProperty.put(property, true);
            mMixpanel.registerSuperProperties(superProperty);
        } catch (JSONException e) {
            AppLog.e(AppLog.T.UTILS, e);
        }
    }

    private void savePropertyValueForStat(String property, Object value, WPStats.Stat stat) {
        JSONObject properties = aggregatedProperties.get(stat);
        if (properties == null) {
            properties = new JSONObject();
            aggregatedProperties.put(stat, properties);
        }

        try {
            properties.put(property, value);
        } catch (JSONException e) {
            AppLog.e(AppLog.T.UTILS, e);
        }
    }

    private JSONObject propertiesForStat(WPStats.Stat stat) {
        return aggregatedProperties.get(stat);
    }

    private void removePropertiesForStat(WPStats.Stat stat) {
        aggregatedProperties.remove(stat);
    }

    private Object propertyForStat(String property, WPStats.Stat stat) {
        JSONObject properties = aggregatedProperties.get(stat);
        if (properties == null)
            return null;

        try {
            Object valueForProperty  = properties.get(property);
            return valueForProperty;
        } catch (JSONException e) {
        }

        return null;
    }

    private void incrementProperty(String property, WPStats.Stat stat) {
        Object currentValueObj = propertyForStat(property, stat);
        int currentValue = 1;
        if (currentValueObj != null) {
            currentValue = Integer.valueOf(currentValueObj.toString());
            currentValue++;
        }

        savePropertyValueForStat(property, Integer.toString(currentValue), stat);
    }

}
