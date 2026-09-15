/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;


public class MessageInfo extends Entity {
    private final Object obj;

    public MessageInfo(Object obj) {
        super(obj);
        this.obj = obj;
    }

    /**
     * The wire name of the message type ("media", "raven_media", "voice_media", ...). It lives on
     * an enum hung off the message; both field names are rewritten at patch time.
     */
    public String getMessageType() throws Exception {
        Entity messageTypeDetailEntity = super.getFieldAsEntity("itemTypeField");
        return (String) messageTypeDetailEntity.getField("itemTypeNameField");
    }

    public MediaData getAudioMedia() throws Exception {
        // The hook passes the message itself, so the audio field is read straight off it.
        Entity audioDataEntity = super.getFieldAsEntity("audioField");
        if(audioDataEntity!=null){
            Object mediaData = audioDataEntity.getField("audioMediaField");
            if(mediaData!=null)
                return new MediaData(mediaData);
        }
        return null;

    }
}
