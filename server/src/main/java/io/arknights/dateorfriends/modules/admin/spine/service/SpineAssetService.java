package io.arknights.dateorfriends.modules.admin.spine.service;

import io.arknights.dateorfriends.modules.admin.spine.mapper.SpineAssetDO;
import io.arknights.dateorfriends.modules.admin.spine.mapper.SpineAssetFileDO;
import io.arknights.dateorfriends.modules.admin.spine.mapper.SpineAssetFileMapper;
import io.arknights.dateorfriends.modules.admin.spine.mapper.SpineAssetMapper;
import io.arknights.dateorfriends.tools.storage.SpineStorageProperties;
import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class SpineAssetService {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final SpineAssetMapper assetMapper;
    private final SpineAssetFileMapper fileMapper;
    private final SpineStorageProperties properties;

    public SpineAssetService(SpineAssetMapper assetMapper, SpineAssetFileMapper fileMapper, SpineStorageProperties properties) {
        this.assetMapper = assetMapper;
        this.fileMapper = fileMapper;
        this.properties = properties;
    }

    public record PageResponse<T>(long total, int page, int size, List<T> items) {
    }

    public record FileItem(
            long id,
            String fileType,
            String originalName,
            String storedName,
            String relativePath,
            long sizeBytes,
            String mimeType,
            String url,
            LocalDateTime createdAt
    ) {
    }

    public record AssetItem(
            long id,
            String assetKey,
            String name,
            int type,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record DetailResponse(AssetItem asset, List<FileItem> files) {
    }

    private static int normalizeType(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 1;
        int v;
        try {
            v = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "type 需为 1(人物)/2(敌人)/3(BOSS)");
        }
        if (v < 1 || v > 3) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "type 需为 1(人物)/2(敌人)/3(BOSS)");
        }
        return v;
    }

    private static String safeFilename(String raw) {
        var name = raw == null ? "" : raw.trim();
        if (name.isBlank()) return "";
        name = name.replace("\\", "/");
        var idx = name.lastIndexOf('/');
        if (idx >= 0) name = name.substring(idx + 1);
        name = name.replace("\u0000", "");
        return name;
    }

    private static String extLower(String filename) {
        var n = safeFilename(filename);
        var idx = n.lastIndexOf('.');
        if (idx < 0) return "";
        return n.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseName(String filename) {
        var n = safeFilename(filename);
        var idx = n.lastIndexOf('.');
        if (idx < 0) return n;
        return n.substring(0, idx);
    }

    private static String normalizeKey(String raw) {
        var k = raw == null ? "" : raw.trim();
        if (k.isBlank()) return "";
        if (!KEY_PATTERN.matcher(k).matches()) return "";
        return k;
    }

    private Path baseDir() {
        var v = properties.getBaseDir();
        if (v == null || v.trim().isBlank()) {
            return Path.of("./data/spine");
        }
        return Path.of(v.trim());
    }

    private Path assetDir(String key) {
        return baseDir().resolve(key);
    }

    private static String buildPublicUrl(String assetKey, String storedName) {
        return "/assets/spine/" + assetKey + "/" + storedName;
    }

    private static String guessMimeByExt(String filename) {
        var e = extLower(filename);
        if ("png".equals(e)) return MediaType.IMAGE_PNG_VALUE;
        if ("atlas".equals(e)) return MediaType.TEXT_PLAIN_VALUE;
        if ("skel".equals(e)) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private record ZipMaterial(String assetKey, String atlasName, Path atlasPath, String skelName, Path skelPath, List<String> pngNames, List<Path> pngPaths, List<String> extraNames, List<Path> extraPaths) {
    }

    private ZipMaterial extractZip(Path zipFile, Path workDir) {
        var extractDir = workDir.resolve("extracted");
        try {
            Files.createDirectories(extractDir);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建临时目录失败");
        }

        String atlasName = null;
        Path atlasPath = null;
        String skelName = null;
        Path skelPath = null;
        var pngNames = new ArrayList<String>();
        var pngPaths = new ArrayList<Path>();
        var extraNames = new ArrayList<String>();
        var extraPaths = new ArrayList<Path>();

        try (InputStream in = Files.newInputStream(zipFile); ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                var rawName = entry.getName();
                if (rawName == null || rawName.isBlank()) continue;
                if (rawName.contains("/") || rawName.contains("\\") || rawName.contains("..")) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "zip 内不支持目录或非法路径");
                }
                var fileName = safeFilename(rawName);
                if (fileName.isBlank()) continue;
                var target = extractDir.resolve(fileName).normalize();
                if (!target.startsWith(extractDir)) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "zip 内文件名非法");
                }
                if (Files.exists(target)) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "zip 内存在重复文件名：" + fileName);
                }
                Files.copy(zis, target);

                var ext = extLower(fileName);
                if ("atlas".equals(ext)) {
                    if (atlasName != null) throw new BusinessException(ErrorCode.PARAM_INVALID, "zip 内只能包含 1 个 atlas 文件");
                    atlasName = fileName;
                    atlasPath = target;
                } else if ("skel".equals(ext)) {
                    if (skelName != null) throw new BusinessException(ErrorCode.PARAM_INVALID, "zip 内只能包含 1 个 skel 文件");
                    skelName = fileName;
                    skelPath = target;
                } else if ("png".equals(ext)) {
                    pngNames.add(fileName);
                    pngPaths.add(target);
                } else {
                    extraNames.add(fileName);
                    extraPaths.add(target);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "zip 解析失败");
        }

        if (atlasName == null || skelName == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "zip 内必须包含 atlas 与 skel 文件");
        }
        if (pngNames.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "zip 内必须包含至少 1 张 png");
        }

        var key1 = normalizeKey(baseName(atlasName));
        var key2 = normalizeKey(baseName(skelName));
        if (key1.isBlank() || key2.isBlank() || !Objects.equals(key1, key2)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "atlas/skel 文件名不一致或不符合命名规范（仅允许字母/数字/_/-，长度<=64）");
        }
        return new ZipMaterial(key1, atlasName, atlasPath, skelName, skelPath, pngNames, pngPaths, extraNames, extraPaths);
    }

    private void copyToAssetDir(String assetKey, ZipMaterial m) throws IOException {
        Files.createDirectories(assetDir(assetKey));
        Files.copy(m.atlasPath(), assetDir(assetKey).resolve(assetKey + ".atlas"));
        Files.copy(m.skelPath(), assetDir(assetKey).resolve(assetKey + ".skel"));
        for (int i = 0; i < m.pngNames().size(); i++) {
            var name = m.pngNames().get(i);
            Files.copy(m.pngPaths().get(i), assetDir(assetKey).resolve(name));
        }
        for (int i = 0; i < m.extraNames().size(); i++) {
            var name = m.extraNames().get(i);
            Files.copy(m.extraPaths().get(i), assetDir(assetKey).resolve(name));
        }
    }

    public Mono<DetailResponse> createFromZip(long actorId, String name, String type, String idleAnimation, String moveAnimation, Double displayScale, FilePart zip) {
        if (zip == null) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "缺少 zip 文件"));
        var zipName = safeFilename(zip.filename());
        if (!Objects.equals(extLower(zipName), "zip")) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "zip 文件格式不正确"));
        }
        var safeName = name == null ? "" : name.trim();
        if (safeName.length() > 128) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "name 长度需<=128"));
        var safeType = normalizeType(type);
        var safeIdle = idleAnimation == null || idleAnimation.trim().isBlank() ? null : idleAnimation.trim();
        var safeMove = moveAnimation == null || moveAnimation.trim().isBlank() ? null : moveAnimation.trim();
        var safeScale = displayScale != null && displayScale > 0 ? displayScale : 1.0;

        return Mono.fromCallable(() -> Files.createTempDirectory("spine-zip-"))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(workDir -> {
                    var zipPath = workDir.resolve("upload.zip");
                    return zip.transferTo(zipPath)
                            .then(Mono.fromCallable(() -> {
                                        try {
                                            var m = extractZip(zipPath, workDir);
                                            var existing = assetMapper.selectByKey(m.assetKey());
                                            if (existing != null) throw new BusinessException(ErrorCode.PARAM_INVALID, "asset_key 已存在");

                                            var asset = new SpineAssetDO();
                                            asset.setAssetKey(m.assetKey());
                                            asset.setName(safeName.isBlank() ? null : safeName);
                                            asset.setType(safeType);
                                            asset.setIdleAnimation(safeIdle);
                                            asset.setMoveAnimation(safeMove);
                                            asset.setDisplayScale(safeScale);
                                            asset.setCreatedBy(actorId);
                                            asset.setUpdatedBy(actorId);
                                            assetMapper.insertAsset(asset);

                                            deletePathQuietly(assetDir(m.assetKey()));
                                            try {
                                                copyToAssetDir(m.assetKey(), m);
                                            } catch (IOException e) {
                                                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件落盘失败");
                                            }

                                            fileMapper.deleteByAssetId(asset.getId());
                                            var files = new ArrayList<SpineAssetFileDO>();

                                            files.add(buildFile("ATLAS", m.atlasName(), m.assetKey() + ".atlas", m.assetKey() + "/" + m.assetKey() + ".atlas"));
                                            files.add(buildFile("SKEL", m.skelName(), m.assetKey() + ".skel", m.assetKey() + "/" + m.assetKey() + ".skel"));
                                            for (var n : m.pngNames()) {
                                                files.add(buildFile("PNG", n, n, m.assetKey() + "/" + n));
                                            }
                                            for (var n : m.extraNames()) {
                                                files.add(buildFile("OTHER", n, n, m.assetKey() + "/" + n));
                                            }

                                            for (var f : files) {
                                                f.setAssetId(asset.getId());
                                                var abs = assetDir(m.assetKey()).resolve(f.getStoredName());
                                                f.setSizeBytes(Files.size(abs));
                                                f.setMimeType(guessMimeByExt(f.getStoredName()));
                                                fileMapper.insertFile(f);
                                            }

                                            var refreshed = assetMapper.selectById(asset.getId());
                                            var list = fileMapper.listByAssetId(asset.getId());
                                            return new DetailResponse(toItem(refreshed), list.stream().map(x -> toFileItem(m.assetKey(), x)).toList());
                                        } finally {
                                            deletePathQuietly(workDir);
                                        }
                                    })
                                    .subscribeOn(Schedulers.boundedElastic()));
                });
    }

    public Mono<DetailResponse> updateFromZip(long actorId, long id, String name, String type, String idleAnimation, String moveAnimation, Double displayScale, FilePart zip) {
        if (zip == null) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "缺少 zip 文件"));
        var zipName = safeFilename(zip.filename());
        if (!Objects.equals(extLower(zipName), "zip")) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "zip 文件格式不正确"));
        }
        var safeName = name == null ? "" : name.trim();
        if (safeName.length() > 128) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "name 长度需<=128"));
        var safeType = normalizeType(type);
        var safeIdle = idleAnimation == null || idleAnimation.trim().isBlank() ? null : idleAnimation.trim();
        var safeMove = moveAnimation == null || moveAnimation.trim().isBlank() ? null : moveAnimation.trim();
        var safeScale = displayScale != null && displayScale > 0 ? displayScale : 1.0;

        return Mono.fromCallable(() -> Files.createTempDirectory("spine-zip-"))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(workDir -> {
                    var zipPath = workDir.resolve("upload.zip");
                    return zip.transferTo(zipPath)
                            .then(Mono.fromCallable(() -> {
                                        try {
                                            var asset = assetMapper.selectById(id);
                                            if (asset == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "资源不存在");
                                            var assetKey = asset.getAssetKey();

                                            var m = extractZip(zipPath, workDir);
                                            if (!Objects.equals(assetKey, m.assetKey())) {
                                                throw new BusinessException(ErrorCode.PARAM_INVALID, "zip 内资源 key 与当前资源不一致");
                                            }

                                            assetMapper.updateAsset(id, safeName.isBlank() ? null : safeName, safeType, safeIdle, safeMove, safeScale, actorId);

                                            deletePathQuietly(assetDir(assetKey));
                                            try {
                                                copyToAssetDir(assetKey, m);
                                            } catch (IOException e) {
                                                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件落盘失败");
                                            }

                                            fileMapper.deleteByAssetId(id);
                                            var files = new ArrayList<SpineAssetFileDO>();

                                            files.add(buildFile("ATLAS", m.atlasName(), assetKey + ".atlas", assetKey + "/" + assetKey + ".atlas"));
                                            files.add(buildFile("SKEL", m.skelName(), assetKey + ".skel", assetKey + "/" + assetKey + ".skel"));
                                            for (var n : m.pngNames()) {
                                                files.add(buildFile("PNG", n, n, assetKey + "/" + n));
                                            }
                                            for (var n : m.extraNames()) {
                                                files.add(buildFile("OTHER", n, n, assetKey + "/" + n));
                                            }

                                            for (var f : files) {
                                                f.setAssetId(id);
                                                var abs = assetDir(assetKey).resolve(f.getStoredName());
                                                f.setSizeBytes(Files.size(abs));
                                                f.setMimeType(guessMimeByExt(f.getStoredName()));
                                                fileMapper.insertFile(f);
                                            }

                                            var refreshed = assetMapper.selectById(id);
                                            var list = fileMapper.listByAssetId(id);
                                            return new DetailResponse(toItem(refreshed), list.stream().map(x -> toFileItem(assetKey, x)).toList());
                                        } finally {
                                            deletePathQuietly(workDir);
                                        }
                                    })
                                    .subscribeOn(Schedulers.boundedElastic()));
                });
    }

    private SpineAssetFileDO buildFile(String fileType, String originalName, String storedName, String relativePath) {
        var f = new SpineAssetFileDO();
        f.setFileType(fileType);
        f.setOriginalName(originalName);
        f.setStoredName(storedName);
        f.setRelativePath(relativePath);
        f.setSizeBytes(0L);
        f.setMimeType(null);
        return f;
    }

    private void deletePathQuietly(Path dir) {
        if (dir == null) return;
        try {
            if (!Files.exists(dir)) return;
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public Mono<PageResponse<AssetItem>> list(String keyword, int page, int size) {
        var p = Math.max(1, page);
        var s = Math.min(200, Math.max(1, size));
        var offset = (p - 1) * s;
        var kw = keyword == null ? "" : keyword.trim();

        return Mono.fromCallable(() -> {
                    var total = assetMapper.count(kw);
                    var list = assetMapper.list(kw, null, offset, s);
                    var items = list.stream().map(this::toItem).toList();
                    return new PageResponse<>(total, p, s, items);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<DetailResponse> detail(long id) {
        return Mono.fromCallable(() -> {
                    var asset = assetMapper.selectById(id);
                    if (asset == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "资源不存在");
                    var files = fileMapper.listByAssetId(id);
                    return new DetailResponse(toItem(asset), files.stream().map(f -> toFileItem(asset.getAssetKey(), f)).toList());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<DetailResponse> create(long actorId, String name, String type, String idleAnimation, String moveAnimation, Double displayScale, FilePart atlas, FilePart skel, List<FilePart> pngs, List<FilePart> extras) {
        if (atlas == null || skel == null) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "缺少 atlas/skel 文件"));
        if (pngs == null || pngs.isEmpty()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "缺少 png 文件"));

        var atlasName = safeFilename(atlas.filename());
        var skelName = safeFilename(skel.filename());
        if (!Objects.equals(extLower(atlasName), "atlas")) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "atlas 文件格式不正确"));
        if (!Objects.equals(extLower(skelName), "skel")) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "skel 文件格式不正确"));

        var key1 = normalizeKey(baseName(atlasName));
        var key2 = normalizeKey(baseName(skelName));
        if (key1.isBlank() || key2.isBlank() || !Objects.equals(key1, key2)) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "atlas/skel 文件名不一致或不符合命名规范（仅允许字母/数字/_/-，长度<=64）"));
        }
        var assetKey = key1;

        var safeName = name == null ? "" : name.trim();
        if (safeName.length() > 128) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "name 长度需<=128"));
        var safeType = normalizeType(type);
        var safeIdle = idleAnimation == null || idleAnimation.trim().isBlank() ? null : idleAnimation.trim();
        var safeMove = moveAnimation == null || moveAnimation.trim().isBlank() ? null : moveAnimation.trim();
        var safeScale = displayScale != null && displayScale > 0 ? displayScale : 1.0;

        var allFiles = new ArrayList<FilePart>();
        allFiles.add(atlas);
        allFiles.add(skel);
        allFiles.addAll(pngs);
        if (extras != null) allFiles.addAll(extras);

        return Mono.fromCallable(() -> assetMapper.selectByKey(assetKey))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existing -> {
                    if (existing != null) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "asset_key 已存在"));
                    return Mono.fromCallable(() -> {
                                var asset = new SpineAssetDO();
                                asset.setAssetKey(assetKey);
                                asset.setName(safeName.isBlank() ? null : safeName);
                                asset.setType(safeType);
                                asset.setIdleAnimation(safeIdle);
                                asset.setMoveAnimation(safeMove);
                                asset.setDisplayScale(safeScale);
                                asset.setCreatedBy(actorId);
                                asset.setUpdatedBy(actorId);
                                assetMapper.insertAsset(asset);
                                return asset;
                            })
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .flatMap(asset -> ensureDir(assetKey)
                        .then(storeAll(assetKey, atlas, skel, pngs, extras)
                                .flatMap(files -> Mono.fromCallable(() -> {
                                            for (var f : files) {
                                                f.setAssetId(asset.getId());
                                                fileMapper.insertFile(f);
                                            }
                                            var refreshed = assetMapper.selectById(asset.getId());
                                            var list = fileMapper.listByAssetId(asset.getId());
                                            return new DetailResponse(toItem(refreshed), list.stream().map(x -> toFileItem(assetKey, x)).toList());
                                        })
                                        .subscribeOn(Schedulers.boundedElastic()))
                        ));
    }

    public Mono<DetailResponse> update(long actorId, long id, String name, String type, String idleAnimation, String moveAnimation, Double displayScale, FilePart atlas, FilePart skel, List<FilePart> pngs, List<FilePart> extras) {
        // 部分更新：未传的字段保持原值；传了但为空的 name/动画视为清除；缩放<=0 视为 1.0
        var hasName = name != null;
        var safeName = name == null ? null : name.trim();
        if (safeName != null && safeName.length() > 128) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "name 长度需<=128"));
        var hasType = type != null;
        var safeType = type == null ? 0 : normalizeType(type);
        var hasIdle = idleAnimation != null;
        var safeIdle = idleAnimation == null ? null : (idleAnimation.trim().isBlank() ? null : idleAnimation.trim());
        var hasMove = moveAnimation != null;
        var safeMove = moveAnimation == null ? null : (moveAnimation.trim().isBlank() ? null : moveAnimation.trim());
        var hasScale = displayScale != null;
        var safeScale = displayScale == null ? null : (displayScale > 0 ? displayScale : 1.0);

        var hasFileUpdate = atlas != null || skel != null || (pngs != null && !pngs.isEmpty()) || (extras != null && !extras.isEmpty());

        return Mono.fromCallable(() -> assetMapper.selectById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(asset -> {
                    if (asset == null) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "资源不存在"));
                    var assetKey = asset.getAssetKey();
                    return Mono.fromCallable(() -> {
                                assetMapper.updateAssetPartial(id, safeName, hasName, safeType, hasType, safeIdle, hasIdle, safeMove, hasMove, safeScale, hasScale, actorId);
                                return 1;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .then(hasFileUpdate ? replaceFiles(assetKey, id, atlas, skel, pngs, extras) : Mono.empty())
                            .then(detail(id));
                });
    }

    public Mono<Void> delete(long id) {
        return Mono.fromCallable(() -> assetMapper.selectById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(asset -> {
                    if (asset == null) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "资源不存在"));
                    var assetKey = asset.getAssetKey();
                    return Mono.fromCallable(() -> {
                                assetMapper.deleteAsset(id);
                                return 1;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .then(deleteDir(assetKey));
                })
                .then();
    }

    private Mono<Void> ensureDir(String assetKey) {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(assetDir(assetKey));
                    return 1;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<List<SpineAssetFileDO>> storeAll(String assetKey, FilePart atlas, FilePart skel, List<FilePart> pngs, List<FilePart> extras) {
        var list = new ArrayList<Mono<SpineAssetFileDO>>();
        list.add(store(assetKey, "ATLAS", atlas, assetKey + ".atlas"));
        list.add(store(assetKey, "SKEL", skel, assetKey + ".skel"));
        for (var p : pngs) {
            var n = safeFilename(p.filename());
            if (!Objects.equals(extLower(n), "png")) {
                return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "png 文件格式不正确"));
            }
            list.add(store(assetKey, "PNG", p, n));
        }
        if (extras != null) {
            for (var e : extras) {
                var n = safeFilename(e.filename());
                if (n.isBlank()) continue;
                if (Set.of("atlas", "skel").contains(extLower(n))) continue;
                list.add(store(assetKey, "OTHER", e, n));
            }
        }
        return Flux.concat(list).collectList();
    }

    private Mono<Void> replaceFiles(String assetKey, long assetId, FilePart atlas, FilePart skel, List<FilePart> pngs, List<FilePart> extras) {
        var hasCore = atlas != null || skel != null || (pngs != null && !pngs.isEmpty());
        if (hasCore) {
            if (atlas == null || skel == null || pngs == null || pngs.isEmpty()) {
                return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "更新文件需同时提供 atlas、skel、png"));
            }
            var atlasName = safeFilename(atlas.filename());
            var skelName = safeFilename(skel.filename());
            if (!Objects.equals(extLower(atlasName), "atlas")) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "atlas 文件格式不正确"));
            if (!Objects.equals(extLower(skelName), "skel")) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "skel 文件格式不正确"));
            var key1 = normalizeKey(baseName(atlasName));
            var key2 = normalizeKey(baseName(skelName));
            if (key1.isBlank() || key2.isBlank() || !Objects.equals(key1, key2) || !Objects.equals(key1, assetKey)) {
                return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "atlas/skel 文件名必须与资源标识一致"));
            }
        }

        return deleteDir(assetKey)
                .then(ensureDir(assetKey))
                .then(storeAll(assetKey, atlas, skel, pngs, extras)
                        .flatMap(files -> Mono.fromCallable(() -> {
                                    fileMapper.deleteByAssetId(assetId);
                                    for (var f : files) {
                                        f.setAssetId(assetId);
                                        fileMapper.insertFile(f);
                                    }
                                    return 1;
                                })
                                .subscribeOn(Schedulers.boundedElastic())))
                .then();
    }

    private Mono<SpineAssetFileDO> store(String assetKey, String fileType, FilePart file, String storedName) {
        var originName = safeFilename(file.filename());
        if (originName.isBlank()) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "文件名不能为空"));
        if (storedName.contains("..") || storedName.contains("/") || storedName.contains("\\")) {
            return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "非法文件名"));
        }

        var dir = assetDir(assetKey);
        var target = dir.resolve(storedName).normalize();
        if (!target.startsWith(dir.normalize())) return Mono.error(new BusinessException(ErrorCode.PARAM_INVALID, "非法路径"));

        var mime = file.headers() == null || file.headers().getContentType() == null ? null : file.headers().getContentType().toString();

        return ensureDir(assetKey)
                .then(file.transferTo(target))
                .then(Mono.fromCallable(() -> Files.size(target)).subscribeOn(Schedulers.boundedElastic()))
                .map(size -> {
                    var f = new SpineAssetFileDO();
                    f.setFileType(fileType);
                    f.setOriginalName(originName);
                    f.setStoredName(storedName);
                    f.setRelativePath(assetKey + "/" + storedName);
                    f.setSizeBytes(size);
                    f.setMimeType(mime);
                    return f;
                });
    }

    private Mono<Void> deleteDir(String assetKey) {
        var dir = assetDir(assetKey);
        return Mono.fromCallable(() -> {
                    if (!Files.exists(dir)) return 1;
                    Files.walk(dir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                    return 1;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private AssetItem toItem(SpineAssetDO d) {
        return new AssetItem(
                d.getId() == null ? 0 : d.getId(),
                d.getAssetKey(),
                d.getName(),
                d.getType() == null ? 1 : d.getType(),
                d.getCreatedBy() == null ? 0 : d.getCreatedBy(),
                d.getUpdatedBy() == null ? 0 : d.getUpdatedBy(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    private FileItem toFileItem(String assetKey, SpineAssetFileDO f) {
        var url = buildPublicUrl(assetKey, f.getStoredName());
        return new FileItem(
                f.getId() == null ? 0 : f.getId(),
                f.getFileType(),
                f.getOriginalName(),
                f.getStoredName(),
                f.getRelativePath(),
                f.getSizeBytes() == null ? 0 : f.getSizeBytes(),
                f.getMimeType(),
                url,
                f.getCreatedAt()
        );
    }
}
