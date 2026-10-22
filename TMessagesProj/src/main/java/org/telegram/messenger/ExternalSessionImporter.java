package org.telegram.messenger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import android.util.Base64;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;

/** Imports auth keys from Telethon StringSession or Pyrogram StringSession. */
public final class ExternalSessionImporter {
    public static final class Session {
        public final int dcId;
        public final byte[] authKey;
        Session(int dcId, byte[] authKey) { this.dcId = dcId; this.authKey = authKey; }
    }
    private ExternalSessionImporter() {}

    public static Session parse(String value) throws Exception {
        if (value == null) {
            throw new IllegalArgumentException("Пустая строка сессии");
        }
        String encoded = value.trim();
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("Пустая строка сессии");
        }

        // Telethon and Pyrogram StringSession exports start with a version
        // marker (currently "1") followed by URL-safe Base64 without padding.
        // Try the version-stripped payload first, then the whole value for
        // compatibility with wrappers that omit the marker.
        Exception firstError = null;
        if (encoded.length() > 1 && encoded.charAt(0) == '1') {
            try {
                return parsePayload(decodeBase64(encoded.substring(1)));
            } catch (Exception e) {
                firstError = e;
            }
        }
        try {
            return parsePayload(decodeBase64(encoded));
        } catch (Exception e) {
            if (firstError != null) {
                e.addSuppressed(firstError);
            }
            throw new IllegalArgumentException(
                    "Неизвестный формат. Нужна Telethon или Pyrogram StringSession.", e);
        }
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (IllegalArgumentException e) {
            return Base64.decode(value, Base64.DEFAULT);
        }
    }

    private static Session parsePayload(byte[] data) {
        if (data.length == 263 || data.length == 275) {
            // Telethon payload: dc_id + 4/16-byte address + port + auth_key.
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            int dc = b.get() & 0xff;
            int addressLength = data.length == 263 ? 4 : 16;
            b.position(1 + addressLength + 2);
            byte[] key = new byte[256];
            b.get(key);
            validate(dc, key);
            return new Session(dc, key);
        }
        if (data.length == 271) {
            // Pyrogram payload: dc_id + api_id + test_mode + auth_key + user_id + is_bot.
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            int dc = b.get() & 0xff;
            b.getInt();
            b.get();
            byte[] key = new byte[256];
            b.get(key);
            validate(dc, key);
            return new Session(dc, key);
        }
        throw new IllegalArgumentException("Неподдерживаемая длина сессии: " + data.length);
    }

    public static Session parseFile(File file) throws Exception {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            Cursor c = db.rawQuery("SELECT dc_id, auth_key FROM sessions LIMIT 1", null);
            try {
                if (!c.moveToFirst()) throw new IllegalArgumentException("В файле нет сессии");
                int dc = c.getInt(0);
                byte[] key = c.getBlob(1);
                validate(dc, key);
                return new Session(dc, key);
            } finally { c.close(); }
        } finally { db.close(); }
    }

    private static void validate(int dc, byte[] key) {
        if (dc < 1 || dc > 5 || key.length != 256) throw new IllegalArgumentException("Некорректный DC/auth_key");
        boolean zero = true; for (byte v : key) if (v != 0) { zero = false; break; }
        if (zero) throw new IllegalArgumentException("Пустой auth_key");
    }
}
