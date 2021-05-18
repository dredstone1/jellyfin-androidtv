package org.jellyfin.androidtv.util.profile;

import androidx.annotation.NonNull;

import org.jellyfin.androidtv.constant.CodecTypes;
import org.jellyfin.androidtv.constant.ContainerTypes;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.util.DeviceUtils;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.apiclient.model.dlna.CodecProfile;
import org.jellyfin.apiclient.model.dlna.CodecType;
import org.jellyfin.apiclient.model.dlna.DeviceProfile;
import org.jellyfin.apiclient.model.dlna.DirectPlayProfile;
import org.jellyfin.apiclient.model.dlna.DlnaProfileType;
import org.jellyfin.apiclient.model.dlna.ProfileCondition;
import org.jellyfin.apiclient.model.dlna.ProfileConditionType;
import org.jellyfin.apiclient.model.dlna.ProfileConditionValue;
import org.jellyfin.apiclient.model.dlna.SubtitleDeliveryMethod;
import org.jellyfin.apiclient.model.dlna.SubtitleProfile;
import org.jellyfin.apiclient.model.dlna.TranscodingProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

import static org.koin.java.KoinJavaComponent.get;

public class ProfileHelper {
    private static MediaCodecCapabilitiesTest MediaTest = new MediaCodecCapabilitiesTest();

    public static void setExoOptions(DeviceProfile profile, boolean isLiveTv, boolean allowDTS) {
        profile.setName("Android-Exo");

        List<DirectPlayProfile> directPlayProfiles = new ArrayList<>();
        if (!isLiveTv || get(UserPreferences.class).get(UserPreferences.Companion.getLiveTvDirectPlayEnabled())) {
            DirectPlayProfile videoDirectPlayProfile = new DirectPlayProfile();
            List<String> containers = new ArrayList<>();
            if (isLiveTv) {
                containers.add(ContainerTypes.TS);
                containers.add(ContainerTypes.MPEGTS);
            }
            containers.addAll(Arrays.asList(
                ContainerTypes.M4V,
                ContainerTypes.MOV,
                ContainerTypes.XVID,
                ContainerTypes.VOB,
                ContainerTypes.MKV,
                ContainerTypes.WMV,
                ContainerTypes.ASF,
                ContainerTypes.OGM,
                ContainerTypes.OGV,
                ContainerTypes.MP4,
                ContainerTypes.WEBM
            ));
            videoDirectPlayProfile.setContainer(Utils.join(",", containers));
            List<String> videoCodecs = Arrays.asList(
                CodecTypes.H264,
                CodecTypes.HEVC,
                CodecTypes.VP8,
                CodecTypes.VP9,
                ContainerTypes.MPEG,
                CodecTypes.MPEG2VIDEO
            );
            videoDirectPlayProfile.setVideoCodec(Utils.join(",", videoCodecs));
            if (Utils.downMixAudio()) {
                //compatible audio mode - will need to transcode dts and ac3
                Timber.i("*** Excluding DTS and AC3 audio from direct play due to compatible audio setting");
                videoDirectPlayProfile.setAudioCodec(Utils.join(",", CodecTypes.AAC, CodecTypes.MP3, CodecTypes.MP2));
            } else {
                List<String> audioCodecs = new ArrayList<>(Arrays.asList(
                    CodecTypes.AAC,
                    CodecTypes.AC3,
                    CodecTypes.EAC3,
                    CodecTypes.AAC_LATM,
                    CodecTypes.MP3,
                    CodecTypes.MP2
                ));
                if (allowDTS) {
                    audioCodecs.add(CodecTypes.DCA);
                    audioCodecs.add(CodecTypes.DTS);
                }
                videoDirectPlayProfile.setAudioCodec(Utils.join(",", audioCodecs));
            }
            videoDirectPlayProfile.setType(DlnaProfileType.Video);
            directPlayProfiles.add(videoDirectPlayProfile);
        }

        DirectPlayProfile audioDirectPlayProfile = new DirectPlayProfile();
        List<String> audioContainers = Arrays.asList(
            CodecTypes.AAC,
            CodecTypes.MP3,
            CodecTypes.MPA,
            CodecTypes.WAV,
            CodecTypes.WMA,
            CodecTypes.MP2,
            ContainerTypes.OGG,
            ContainerTypes.OGA,
            ContainerTypes.WEBMA,
            CodecTypes.APE,
            CodecTypes.OPUS
        );
        audioDirectPlayProfile.setContainer(Utils.join(",", audioContainers));
        audioDirectPlayProfile.setType(DlnaProfileType.Audio);
        directPlayProfiles.add(audioDirectPlayProfile);

        DirectPlayProfile photoDirectPlayProfile = new DirectPlayProfile();
        photoDirectPlayProfile.setContainer("jpg,jpeg,png,gif");
        photoDirectPlayProfile.setType(DlnaProfileType.Photo);
        directPlayProfiles.add(photoDirectPlayProfile);

        DirectPlayProfile[] profiles = new DirectPlayProfile[directPlayProfiles.size()];
        profile.setDirectPlayProfiles(directPlayProfiles.toArray(profiles));

        CodecProfile videoCodecProfile = new CodecProfile();
        videoCodecProfile.setType(CodecType.Video);
        videoCodecProfile.setCodec(CodecTypes.H264);
        videoCodecProfile.setConditions(new ProfileCondition[]
                {
                        new ProfileCondition(ProfileConditionType.EqualsAny, ProfileConditionValue.VideoProfile, "high|main|baseline|constrained baseline"),
                        new ProfileCondition(ProfileConditionType.LessThanEqual, ProfileConditionValue.VideoLevel, DeviceUtils.isFireTvStickGen1()? "41" : "51")
                });

        CodecProfile refFramesProfile = new CodecProfile();
        refFramesProfile.setType(CodecType.Video);
        refFramesProfile.setCodec(CodecTypes.H264);
        refFramesProfile.setConditions(new ProfileCondition[]
                {
                        new ProfileCondition(ProfileConditionType.LessThanEqual, ProfileConditionValue.RefFrames, "12"),
                });
        refFramesProfile.setApplyConditions(new ProfileCondition[] {
                new ProfileCondition(ProfileConditionType.GreaterThanEqual, ProfileConditionValue.Width, "1200")
        });

        CodecProfile refFramesProfile2 = new CodecProfile();
        refFramesProfile2.setType(CodecType.Video);
        refFramesProfile2.setCodec(CodecTypes.H264);
        refFramesProfile2.setConditions(new ProfileCondition[]
                {
                        new ProfileCondition(ProfileConditionType.LessThanEqual, ProfileConditionValue.RefFrames, "4"),
                });
        refFramesProfile2.setApplyConditions(new ProfileCondition[] {
                new ProfileCondition(ProfileConditionType.GreaterThanEqual, ProfileConditionValue.Width, "1900")
        });

        CodecProfile videoAudioCodecProfile = new CodecProfile();
        videoAudioCodecProfile.setType(CodecType.VideoAudio);
        videoAudioCodecProfile.setConditions(new ProfileCondition[]{new ProfileCondition(ProfileConditionType.LessThanEqual, ProfileConditionValue.AudioChannels, "6")});

        profile.setCodecProfiles(new CodecProfile[] { videoCodecProfile, refFramesProfile, refFramesProfile2, getHevcProfile(), videoAudioCodecProfile });
        profile.setSubtitleProfiles(new SubtitleProfile[] {
                getSubtitleProfile("srt", SubtitleDeliveryMethod.External),
                getSubtitleProfile("srt", SubtitleDeliveryMethod.Embed),
                getSubtitleProfile("subrip", SubtitleDeliveryMethod.Embed),
                getSubtitleProfile("ass", SubtitleDeliveryMethod.Encode),
                getSubtitleProfile("ssa", SubtitleDeliveryMethod.Encode),
                getSubtitleProfile("pgs", SubtitleDeliveryMethod.Encode),
                getSubtitleProfile("pgssub", SubtitleDeliveryMethod.Encode),
                getSubtitleProfile("dvdsub", SubtitleDeliveryMethod.Encode),
                getSubtitleProfile("vtt", SubtitleDeliveryMethod.Embed),
                getSubtitleProfile("sub", SubtitleDeliveryMethod.Embed),
                getSubtitleProfile("idx", SubtitleDeliveryMethod.Embed)
        });
    }

    protected static @NonNull CodecProfile getHevcProfile() {
        CodecProfile hevcProfile = new CodecProfile();
        hevcProfile.setType(CodecType.Video);
        hevcProfile.setCodec(CodecTypes.HEVC);
        if (!MediaTest.supportsHevc()) {
            //The following condition is a method to exclude all HEVC
            Timber.i("*** Does NOT support HEVC");
            hevcProfile.setConditions(new ProfileCondition[]
                    {
                            new ProfileCondition(ProfileConditionType.Equals, ProfileConditionValue.VideoProfile, "none"),
                    });

        } else if (!MediaTest.supportsHevcMain10()) {
            Timber.i("*** Does NOT support HEVC 10 bit");
            hevcProfile.setConditions(new ProfileCondition[]
                    {
                            new ProfileCondition(ProfileConditionType.NotEquals, ProfileConditionValue.VideoProfile, "Main 10"),
                    });

        } else {
            // supports all HEVC
            Timber.i("*** Supports HEVC 10 bit");
            hevcProfile.setConditions(new ProfileCondition[]
                    {
                            new ProfileCondition(ProfileConditionType.NotEquals, ProfileConditionValue.VideoProfile, "none"),
                    });

        }

        return hevcProfile;
    }

    public static void addAc3Streaming(@NonNull DeviceProfile profile, boolean primary) {
        TranscodingProfile mkvProfile = getTranscodingProfile(profile, ContainerTypes.MKV);
        if (mkvProfile != null && !Utils.downMixAudio())
        {
            Timber.i("*** Adding AC3 as supported transcoded audio");
            mkvProfile.setAudioCodec(primary ? CodecTypes.AC3 + ",".concat(mkvProfile.getAudioCodec()) : mkvProfile.getAudioCodec().concat("," + CodecTypes.AC3));
        }
    }

    private static TranscodingProfile getTranscodingProfile(DeviceProfile deviceProfile, String container) {
        for (TranscodingProfile profile : deviceProfile.getTranscodingProfiles()) {
            if (container.equals(profile.getContainer())) return profile;
        }

        return null;
    }

    protected static @NonNull SubtitleProfile getSubtitleProfile(@NonNull String format, @NonNull SubtitleDeliveryMethod method) {
        SubtitleProfile subs = new SubtitleProfile();
        subs.setFormat(format);
        subs.setMethod(method);
        return subs;
    }
}
