package org.schabi.newpipe.streams;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Pair;
import androidx.annotation.Nullable;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Encodes extractor metadata in standard media-container tags. */
public final class MediaMetadata {
    private static final int MAX_ARTWORK_BYTES = 5 * 1024 * 1024;
    private MediaMetadata() { }

    public static List<Pair<String,String>> createVorbisComments(@Nullable final StreamInfo info) {
        final List<Pair<String,String>> tags = new ArrayList<>();
        if (info == null) return tags;
        add(tags,"TITLE",info.getName());
        add(tags,"ARTIST",info.getUploaderName());
        add(tags,"ARTIST_URL",info.getUploaderUrl());
        add(tags,"GENRE",info.getCategory());
        add(tags,"COMMENT",info.getUrl());
        add(tags,"PUBLISHER",info.getHost());
        add(tags,"LICENSE",info.getLicence());
        add(tags,"DATE",info.getTextualUploadDate());
        if (info.getDescription()!=null) add(tags,"DESCRIPTION",info.getDescription().getContent());
        if (info.getDuration()>0) add(tags,"LENGTH",Long.toString(info.getDuration()));
        if (info.getViewCount()>=0) add(tags,"VIEWS",Long.toString(info.getViewCount()));
        if (info.getLikeCount()>=0) add(tags,"LIKES",Long.toString(info.getLikeCount()));
        for (final String tag:info.getTags()) add(tags,"TAG",tag);
        final Artwork art=downloadArtwork(info);
        if (art!=null) add(tags,"METADATA_BLOCK_PICTURE",Base64.encodeToString(picture(art),Base64.NO_WRAP));
        return tags;
    }

    public static byte[] createMp4Udta(@Nullable final StreamInfo info) {
        if (info==null) return null;
        final List<byte[]> items=new ArrayList<>();
        textItem(items,"\u00A9nam",info.getName());
        textItem(items,"\u00A9ART",info.getUploaderName());
        textItem(items,"\u00A9gen",info.getCategory());
        textItem(items,"\u00A9day",info.getTextualUploadDate());
        textItem(items,"\u00A9cmt",info.getUrl());
        if(info.getDescription()!=null) textItem(items,"\u00A9des",info.getDescription().getContent());
        for(final String tag:info.getTags()) textItem(items,"\u00A9key",tag);
        final Artwork art=downloadArtwork(info);
        if(art!=null) items.add(box("covr",dataAtom("image/png".equals(art.mime)?14:13,art.bytes)));
        if(items.isEmpty()) return null;
        final byte[] ilst=box("ilst",concat(items));
        final ByteBuffer h=ByteBuffer.allocate(25).order(ByteOrder.BIG_ENDIAN);
        h.putInt(0).putInt(0).put(new byte[]{'m','d','i','r'}).put(new byte[12]).put((byte)0);
        return box("udta",box("meta",concat(new byte[4],box("hdlr",h.array()),ilst)));
    }

    public static byte[] createId3v24Tag(@Nullable final StreamInfo info) {
        if(info==null) return new byte[0];
        final List<byte[]> frames=new ArrayList<>();
        id3Text(frames,"TIT2",info.getName());
        id3Text(frames,"TPE1",info.getUploaderName());
        id3Text(frames,"TCON",info.getCategory());
        id3Text(frames,"TDRC",info.getTextualUploadDate());
        id3Text(frames,"WOAS",info.getUrl());
        id3Text(frames,"WCOP",info.getLicence());
        if(info.getDescription()!=null) {
            id3Text(frames,"TIT3",info.getDescription().getContent());
            id3Comment(frames,info.getDescription().getContent());
        }
        id3UserIf(frames,"ARTIST_URL",info.getUploaderUrl());
        id3UserIf(frames,"PUBLISHER",info.getHost());
        if(info.getDuration()>0) id3User(frames,"DURATION_SECONDS",Long.toString(info.getDuration()));
        if(info.getViewCount()>=0) id3User(frames,"VIEWS",Long.toString(info.getViewCount()));
        if(info.getLikeCount()>=0) id3User(frames,"LIKES",Long.toString(info.getLikeCount()));
        for(final String tag:info.getTags()) id3User(frames,"TAG",tag);
        final Artwork art=downloadArtwork(info);
        if(art!=null) {
            final ByteArrayOutputStream b=new ByteArrayOutputStream();
            b.write(3); ascii(b,art.mime); b.write(0); b.write(3); b.write(0);
            b.write(art.bytes,0,art.bytes.length);
            frames.add(id3Frame("APIC",b.toByteArray()));
        }
        final byte[] body=concat(frames);
        if(body.length==0) return body;
        final ByteBuffer header=ByteBuffer.allocate(10);
        header.put(new byte[]{'I','D','3',4,0,0}).put(syncSafe(body.length));
        return concat(header.array(),body);
    }

    private static void add(final List<Pair<String,String>> tags,String key,@Nullable String val) {
        if(val!=null&&!val.trim().isEmpty()) tags.add(Pair.create(key,val.trim()));
    }
    private static void textItem(final List<byte[]> items,String key,@Nullable String val) {
        if(val!=null&&!val.trim().isEmpty()) items.add(box(key,dataAtom(1,val.trim().getBytes(StandardCharsets.UTF_8))));
    }
    private static byte[] dataAtom(int type,byte[] data) {
        final ByteBuffer b=ByteBuffer.allocate(16+data.length).order(ByteOrder.BIG_ENDIAN);
        b.putInt(16+data.length).put(new byte[]{'d','a','t','a'}).putInt(type).putInt(0).put(data);
        return b.array();
    }
    private static void id3Text(List<byte[]> out,String id,@Nullable String value) {
        if(value==null||value.trim().isEmpty()) return;
        final byte[] txt=value.trim().getBytes(StandardCharsets.UTF_8);
        final byte[] payload=new byte[txt.length+1]; payload[0]=3;
        System.arraycopy(txt,0,payload,1,txt.length); out.add(id3Frame(id,payload));
    }
    private static void id3Comment(List<byte[]> out,String value) {
        final ByteArrayOutputStream b=new ByteArrayOutputStream();
        b.write(3); ascii(b,"eng"); b.write(0);
        final byte[] txt=value.getBytes(StandardCharsets.UTF_8); b.write(txt,0,txt.length);
        out.add(id3Frame("COMM",b.toByteArray()));
    }
    private static void id3UserIf(List<byte[]> out,String key,@Nullable String value) {
        if(value!=null&&!value.trim().isEmpty()) id3User(out,key,value);
    }
    private static void id3User(List<byte[]> out,String key,String value) {
        final ByteArrayOutputStream b=new ByteArrayOutputStream(); b.write(3);
        final byte[] k=key.getBytes(StandardCharsets.UTF_8); b.write(k,0,k.length); b.write(0);
        final byte[] v=value.getBytes(StandardCharsets.UTF_8); b.write(v,0,v.length);
        out.add(id3Frame("TXXX",b.toByteArray()));
    }
    private static byte[] id3Frame(String id,byte[] data) {
        final ByteBuffer b=ByteBuffer.allocate(10+data.length);
        b.put(id.getBytes(StandardCharsets.US_ASCII)).put(syncSafe(data.length)).putShort((short)0).put(data);
        return b.array();
    }
    private static byte[] syncSafe(int n) {
        return new byte[]{(byte)((n>>21)&127),(byte)((n>>14)&127),(byte)((n>>7)&127),(byte)(n&127)};
    }
    private static void ascii(ByteArrayOutputStream out,String val) {
        final byte[] b=val.getBytes(StandardCharsets.US_ASCII); out.write(b,0,b.length);
    }
    private static byte[] picture(Artwork art) {
        final byte[] mime=art.mime.getBytes(StandardCharsets.US_ASCII);
        final byte[] desc="Cover art".getBytes(StandardCharsets.UTF_8);
        final ByteBuffer b=ByteBuffer.allocate(4+4+mime.length+4+desc.length+16+4+art.bytes.length).order(ByteOrder.BIG_ENDIAN);
        b.putInt(3).putInt(mime.length).put(mime).putInt(desc.length).put(desc)
         .putInt(art.width).putInt(art.height).putInt(24).putInt(0).putInt(art.bytes.length).put(art.bytes);
        return b.array();
    }
    private static byte[] box(String type,byte[] payload) {
        final byte[] name=type.getBytes(StandardCharsets.ISO_8859_1);
        if(name.length!=4) throw new IllegalArgumentException("MP4 atom must be four bytes");
        final ByteBuffer b=ByteBuffer.allocate(8+payload.length).order(ByteOrder.BIG_ENDIAN);
        b.putInt(8+payload.length).put(name).put(payload); return b.array();
    }
    private static byte[] concat(List<byte[]> list) {
        final ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] b:list) out.write(b,0,b.length); return out.toByteArray();
    }
    private static byte[] concat(byte[]... list) {
        final ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] b:list) out.write(b,0,b.length); return out.toByteArray();
    }

    @Nullable private static Artwork downloadArtwork(@Nullable StreamInfo info) {
        if(info==null||info.getThumbnails()==null||info.getThumbnails().isEmpty()) return null;
        Image selected=null; long best=-1;
        for(Image image:info.getThumbnails()) {
            if(image==null||image.getUrl()==null||image.getUrl().isEmpty()) continue;
            long area=(long)Math.max(0,image.getWidth())*Math.max(0,image.getHeight());
            if(selected==null||area>best) { selected=image; best=area; }
        }
        if(selected==null) return null;
        HttpURLConnection c=null;
        try {
            URL url=new URL(selected.getUrl());
            if(!"http".equalsIgnoreCase(url.getProtocol())&&!"https".equalsIgnoreCase(url.getProtocol())) return null;
            c=(HttpURLConnection)url.openConnection(); c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(5000); c.setReadTimeout(7000); c.setRequestProperty("User-Agent","NewPipe");
            int response=c.getResponseCode(); if(response<200||response>=300) return null;
            int length=c.getContentLength(); if(length>MAX_ARTWORK_BYTES) return null;
            ByteArrayOutputStream out=new ByteArrayOutputStream(length>0?length:8192);
            try(InputStream in=c.getInputStream()) {
                byte[] buf=new byte[16384]; int n;
                while((n=in.read(buf))!=-1) { if(out.size()+n>MAX_ARTWORK_BYTES) return null; out.write(buf,0,n); }
            }
            byte[] bytes=out.toByteArray(); if(bytes.length==0) return null;
            String mime=c.getContentType();
            if(mime!=null&&mime.contains(";")) mime=mime.substring(0,mime.indexOf(';')).trim();
            if(mime==null||!mime.startsWith("image/")) mime=sniff(bytes);
            if(!"image/jpeg".equalsIgnoreCase(mime)&&!"image/png".equalsIgnoreCase(mime)) {
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length); if(bitmap==null) return null;
                try { ByteArrayOutputStream jpg=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG,90,jpg); bytes=jpg.toByteArray(); mime="image/jpeg"; }
                finally { bitmap.recycle(); }
            }
            BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            int w=selected.getWidth()>0?selected.getWidth():bounds.outWidth;
            int h=selected.getHeight()>0?selected.getHeight():bounds.outHeight;
            return new Artwork(bytes,mime,Math.max(0,w),Math.max(0,h));
        } catch(Exception ignored) { return null; }
        finally { if(c!=null) c.disconnect(); }
    }
    private static String sniff(byte[] b) {
        if(b.length>=8&&(b[0]&255)==0x89&&b[1]==0x50&&b[2]==0x4e&&b[3]==0x47) return "image/png";
        if(b.length>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff) return "image/jpeg";
        return "application/octet-stream";
    }
    private static final class Artwork {
        final byte[] bytes; final String mime; final int width,height;
        Artwork(byte[] bytes,String mime,int width,int height){this.bytes=bytes;this.mime=mime;this.width=width;this.height=height;}
    }
}
