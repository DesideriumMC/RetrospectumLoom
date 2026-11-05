package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Check;
import net.fabricmc.loom.util.ManifestIndex;
import net.fabricmc.loom.util.Props;
import net.fabricmc.loom.util.VersionManifest;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Period;
import java.util.Locale;

public class VanillaJarFetcher extends NewProvider<VanillaJarFetcher> {
	public VanillaJarFetcher(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private ConfigElementWrapper mc;
	private @Nullable String customManifestUrl;
	private String clientFilename, serverFilename;
	
	public VanillaJarFetcher mc(ConfigElementWrapper mc) {
		this.mc = mc;
		props.put("mcversion", mc.getVersion());
		return this;
	}
	
	public VanillaJarFetcher customManifestUrl(@Nullable String customManifestUrl) {
		this.customManifestUrl = customManifestUrl;
		props.put("customManifestUrl", customManifestUrl);
		return this;
	}
	
	public VanillaJarFetcher clientFilename(String clientFilename) {
		this.clientFilename = clientFilename;
		return this;
	}
	
	public VanillaJarFetcher serverFilename(String serverFilename) {
		this.serverFilename = serverFilename;
		return this;
	}
	
	//outputs
	private Path clientJar;
	private Path serverJar;
	private VersionManifest versionManifest;
	
	public Path getClientJar() {
		return clientJar;
	}
	
	public Path getServerJar() {
		return serverJar;
	}
	
	public VersionManifest getVersionManifest() {
		return versionManifest;
	}
	
	//process
	public VanillaJarFetcher fetch() throws Exception {
		Check.notNull(mc, "minecraft version");
		
        ManifestIndex.VersionData selectedVersion;
        if (customManifestUrl != null) {
            selectedVersion = new ManifestIndex.VersionData();
            selectedVersion.id = mc.getVersion();
            selectedVersion.url = customManifestUrl;
        } else {
            Props indexProps = props.copy().remove("mcversion");
            Path index = newDownloadSession("https://launchermeta.mojang.com/mc/game/version_manifest.json")
                .dest(getCacheDir().resolve(indexProps.subst("version_manifest_{HASH}.json")))
                .etag(true).gzip(true).skipIfNewerThan(Period.ofDays(14)).download();
            selectedVersion = ManifestIndex.read(index).versions.get(mc.getVersion().toLowerCase(Locale.ROOT));
            if (selectedVersion == null || selectedVersion.url == null)
                throw new IllegalStateException("No per-version manifest for Minecraft " + mc.getVersion() + " in " + index);
        }
        Path thisVersionManifestJson = newDownloadSession(selectedVersion.url)
            .dest(getCacheDir().resolve(props.subst("minecraft-" + mc.getFilenameSafeVersion() + "-info-{HASH}.json")))
            .etag(true).gzip(true).skipIfSha1Equals(selectedVersion.sha1)
            .skipIfNewerThan(Period.ofDays(14)).download();
		log.lifecycle("] this version manifest: {}", thisVersionManifestJson);
		
		log.info("|-> Parsing per-version manifest...");
		versionManifest = VersionManifest.read(thisVersionManifestJson);
		
        clientJar = downloadGameJar(versionManifest.downloads.get("client"), getCacheDir().resolve(props.subst(clientFilename)));
        serverJar = downloadGameJar(versionManifest.downloads.get("server"), getCacheDir().resolve(props.subst(serverFilename)));
        log.lifecycle("] client jar: {}", clientJar);
        log.lifecycle("] server jar: {}", serverJar);

		return this;
	}
    Path downloadGameJar(VersionManifest.Downloads download, Path target) throws Exception {
        if (download == null || download.url == null) throw new IllegalStateException("Missing game download metadata for " + target);
        boolean missing = download.sha1 == null || download.sha1.trim().isEmpty();
        if ((!missing && !download.sha1.matches("[0-9a-fA-F]{40}")) || (missing && customManifestUrl == null))
            throw new IllegalStateException("Missing or invalid SHA-1 for game download " + target);
        if (missing) log.warn("UNVERIFIED custom game download: no SHA-1 supplied; source={} target={}", download.url, target);
        return newDownloadSession(download.url).dest(target).etag(true).gzip(false)
            .skipIfSha1Equals(missing ? null : download.sha1).skipIfNewerThan(Period.ofDays(14)).download();
    }
}
