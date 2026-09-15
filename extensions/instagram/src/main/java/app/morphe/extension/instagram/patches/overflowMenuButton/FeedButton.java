/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.overflowMenuButton;

import static app.morphe.extension.instagram.utils.IgStr.str;

import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.crimera.ObjectBrowser;

import app.morphe.extension.instagram.patches.Links;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.patches.feed.MoreOptionsOnPostPatch;
import app.morphe.extension.instagram.settings.ActivityHook;

import com.instagram.feed.media.mediaoption.MediaOption$Option;
import com.instagram.common.session.UserSession;

public class FeedButton {

    /**
     * Builds one added menu option, positioned after the app's own.
     *
     * The second constructor argument is the enum's ordinal, and an enum constant's ordinal has to
     * be its index in $VALUES: the app indexes options by it — its own logging does a packed switch
     * on ordinal() — and Kotlin's enum entries assume the same. piko passes a fixed 500, which is
     * hundreds past the end of the array. The added constants are appended to $VALUES in the order
     * below, so their ordinals continue from the original count.
     */
    private static MediaOption$Option initOverflowButton(String tag, int addedIndex, String drawableResName){
        int drawableIconId = ResourceUtils.getIdentifier(ResourceType.DRAWABLE,drawableResName);
        return new MediaOption$Option(tag, MediaOption$Option.$values().length + addedIndex, drawableIconId);
    }

    public static MediaOption$Option[] addToMenuOptionArray() {
        MediaOption$Option[] originalArray = MediaOption$Option.$values();
        List<MediaOption$Option> additionalButtonsList = new ArrayList<>();

        if(SettingsStatus.downloadMedia){
            additionalButtonsList.add(MediaOption$Option.PIKO_DOWNLOAD);
        }
        if(SettingsStatus.moreOptionsOnPost){
            additionalButtonsList.add(MediaOption$Option.PIKO_MORE_POST_OPTION);
        }
        if(SettingsStatus.downloadWithExternalDownloader){
            additionalButtonsList.add(MediaOption$Option.PIKO_EXTERNAL_DOWNLOADER);
        }

        int additionalButtonListSize = additionalButtonsList.size();

        if(additionalButtonListSize > 0) {
            int originalLength = originalArray.length;

            // Create a new array that can hold the existing value as well as newly added buttons.
            MediaOption$Option[] newButtonArray = new MediaOption$Option[additionalButtonListSize + originalLength];

            // Copy elements from the old array to the new array.
            System.arraycopy(originalArray, 0, newButtonArray, 0, originalLength);

            for(MediaOption$Option button : additionalButtonsList){
                newButtonArray[originalLength++] = button;
            }
            return newButtonArray;
        }
        // Returns the original array as a fallback.
        return originalArray;
    }


    private static Class<?> getEnumButtonClass() throws Exception {
        return Class.forName("X.6zl");
    }
    private static Object enumNormalButton() throws Exception {
        return (Object) new Entity().getMethod(getEnumButtonClass(),"valueOf","NORMAL");
    }

    private static void addButton(MediaOption$Option overflowButton, String overflowButtonText, Object buttonAdderObject, ArrayList buttonlist) throws Exception {
        Class<?> clazz = buttonAdderObject.getClass();

        Method method = clazz.getDeclaredMethod(
                "A00",
                getEnumButtonClass(),
                MediaOption$Option.class,
                clazz,
                CharSequence.class,
                ArrayList.class,
                boolean.class
        );

        method.setAccessible(true);
        method.invoke(null, enumNormalButton(), overflowButton, buttonAdderObject, overflowButtonText, buttonlist, false);
    }

    public static MediaOption$Option downloadOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_DOWNLOAD", 0, UI.DRAWABLE_DOWNLOAD_ICON);
    }

    public static MediaOption$Option morePostOptionOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_MORE_POST_OPTION", 1, UI.DRAWABLE_BLUB_ICON);
    }

    public static MediaOption$Option debugOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_DEBUG", 3, UI.DRAWABLE_DEBUG_ICON);
    }

    public static MediaOption$Option externalDownloaderOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_EXTERNAL_DOWNLOADER", 2, UI.DRAWABLE_DOWNLOAD_ICON);
    }


    private static void addDownloadButton(Object buttonAdderObject, ArrayList buttonlist) throws Exception {
        String DOWNLOAD_BUTTON_TEXT = str("piko_download_options");
        if(Pref.enableDirectDownload()){
            DOWNLOAD_BUTTON_TEXT = str("piko_category_download_media");
        }
        addButton(MediaOption$Option.PIKO_DOWNLOAD, DOWNLOAD_BUTTON_TEXT, buttonAdderObject, buttonlist);
    }

    public static void addFeedOverflowButton(Object buttonAdderObject, ArrayList buttonlist){
        try {
            if(Pref.pikoDebug()){
                addButton(MediaOption$Option.PIKO_DEBUG, str("piko_debug"), buttonAdderObject, buttonlist);
            }
            if(Pref.enableDownload()) {
                addDownloadButton(buttonAdderObject, buttonlist);
            }
            if(Pref.downloadWithExternalDownloader()) {
                addButton(MediaOption$Option.PIKO_EXTERNAL_DOWNLOADER, str("piko_download_with_external_downloader"), buttonAdderObject, buttonlist);
            }
            if(Pref.moreOptionsOnPost()) {
                addButton(MediaOption$Option.PIKO_MORE_POST_OPTION, str("piko_more_options"), buttonAdderObject, buttonlist);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Error at addReelButton",e);
        }
    }

    /** The option whose row carries the download action, since an added row is never drawn. */
    private static final String DOWNLOAD_STAND_IN = "SAVE";

    /**
     * Whether this row is the one standing in for the download action.
     *
     * 446's action sheet draws only the options it recognises — it resolves each row's label and
     * icon from the option itself rather than from the row — so an option a patch invents is
     * dropped at render time no matter how correctly it was added to the list. The download action
     * is therefore attached to an option the app already draws, at the cost of that option's own
     * behaviour. Save is the one given up.
     */
    private static boolean isDownloadStandIn(MediaOption$Option pressedButton){
        return SettingsStatus.downloadMedia
                && Pref.enableDownload()
                && DOWNLOAD_STAND_IN.equals(pressedButton.name());
    }

    public static boolean isCustomButtonPressed(MediaOption$Option pressedButton){
        if (isDownloadStandIn(pressedButton)) {
            return true;
        }
        return (
                pressedButton.equals(MediaOption$Option.PIKO_DEBUG) ||
                (SettingsStatus.downloadMedia && pressedButton.equals(MediaOption$Option.PIKO_DOWNLOAD)) ||
                (SettingsStatus.moreOptionsOnPost && pressedButton.equals(MediaOption$Option.PIKO_MORE_POST_OPTION)) ||
                (SettingsStatus.downloadWithExternalDownloader && pressedButton.equals(MediaOption$Option.PIKO_EXTERNAL_DOWNLOADER))
        );
    }

    public static void customButtonOnClick(MediaOption$Option pressedButton, UserSession userSession, Context context, Object mediaObject, int currentMediaIndex){
        try{
            if(pressedButton.equals(MediaOption$Option.PIKO_DEBUG)) {
                ObjectBrowser.browseObject(context, new MediaData(mediaObject, userSession));

            } else if (isDownloadStandIn(pressedButton)
                    || (SettingsStatus.downloadMedia && pressedButton.equals(MediaOption$Option.PIKO_DOWNLOAD))) {
                DownloadUtils.downloadPost(context, userSession, mediaObject, currentMediaIndex);

            } else if (SettingsStatus.moreOptionsOnPost && pressedButton.equals(MediaOption$Option.PIKO_MORE_POST_OPTION)) {
                MoreOptionsOnPostPatch.postMoreOptions(context, userSession, mediaObject, currentMediaIndex);

            } else if (SettingsStatus.downloadWithExternalDownloader && pressedButton.equals(MediaOption$Option.PIKO_EXTERNAL_DOWNLOADER)) {
                DownloadUtils.externalDownloader(mediaObject,currentMediaIndex);

            }

        } catch (Exception e) {
            Logger.printException(() -> "Error at customButtonOnClick",e);
        }
    }

}