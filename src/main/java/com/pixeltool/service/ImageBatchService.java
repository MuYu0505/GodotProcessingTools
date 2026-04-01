package com.pixeltool.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixeltool.dto.ImageEditOptions;
import com.pixeltool.dto.PreviewResponse;
import com.pixeltool.dto.ProcessOptions;
import com.pixeltool.dto.ProcessResponse;
import com.pixeltool.dto.ProcessResultItem;
import com.pixeltool.service.processing.ImageOperation;
import com.pixeltool.service.processing.ProcessingContext;
import com.pixeltool.util.ImageSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ImageBatchService {

    private final List<ImageOperation> operations;
    private final Path storageRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImageBatchService(List<ImageOperation> operations,
                             @Value("${pixel-tool.storage-root:./output}") String storageRoot) throws IOException {
        this.operations = new ArrayList<ImageOperation>(operations);
        this.operations.sort(Comparator.comparingInt(this::operationWeight));
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
        Files.createDirectories(this.storageRoot);
    }

    public ProcessOptions parseOptions(String optionsJson) throws IOException {
        if (optionsJson == null || optionsJson.trim().isEmpty()) {
            return new ProcessOptions();
        }
        return objectMapper.readValue(optionsJson, ProcessOptions.class);
    }

    public List<ProcessOptions> parseOptionsList(String optionsListJson) throws IOException {
        if (optionsListJson == null || optionsListJson.trim().isEmpty()) {
            return null;
        }
        return objectMapper.readValue(optionsListJson, new TypeReference<List<ProcessOptions>>() {
        });
    }

    public List<ImageEditOptions> parseEdits(String editsJson) throws IOException {
        if (editsJson == null || editsJson.trim().isEmpty()) {
            return new ArrayList<ImageEditOptions>();
        }
        return objectMapper.readValue(editsJson, new TypeReference<List<ImageEditOptions>>() {
        });
    }

    public PreviewResponse previewFile(MultipartFile file, ProcessOptions options, ImageEditOptions editOptions) throws IOException {
        ProcessedImages processedImages = processImage(file, options, editOptions);
        PreviewResponse response = new PreviewResponse();
        response.setWidth(processedImages.finalImage.getWidth());
        response.setHeight(processedImages.finalImage.getHeight());
        response.setCroppedUrl(ImageSupport.toDataUrl(processedImages.croppedImage));
        response.setCleanedUrl(ImageSupport.toDataUrl(processedImages.cleanedImage));
        response.setFinalUrl(ImageSupport.toDataUrl(processedImages.finalImage));
        if (processedImages.transparentImage != null) {
            response.setTransparentUrl(ImageSupport.toDataUrl(processedImages.transparentImage));
        }
        if (processedImages.maskImage != null) {
            response.setMaskUrl(ImageSupport.toDataUrl(processedImages.maskImage));
        }
        return response;
    }

    public ProcessResponse processFiles(MultipartFile[] files, ProcessOptions options, List<ImageEditOptions> edits) throws IOException {
        return processFiles(files, options, edits, null);
    }

    public ProcessResponse processFiles(MultipartFile[] files, ProcessOptions options, List<ImageEditOptions> edits, List<ProcessOptions> optionsList) throws IOException {
        if (files == null || files.length == 0) {
            throw new IOException("未检测到上传图片");
        }

        String jobId = UUID.randomUUID().toString().replace("-", "");
        Path jobDir = storageRoot.resolve(jobId);
        Files.createDirectories(jobDir);

        ProcessResponse response = new ProcessResponse();
        response.setJobId(jobId);

        for (int index = 0; index < files.length; index++) {
            MultipartFile file = files[index];
            if (file.isEmpty()) {
                continue;
            }

            ImageEditOptions editOptions = resolveEditOptions(edits, index);
            ProcessOptions resolvedOptions = resolveOptions(options, optionsList, index);
            ProcessedImages processedImages = processImage(file, resolvedOptions, editOptions);

            String baseName = sanitizeFileName(file.getOriginalFilename());
            
            // Generate a unique filename if there are duplicates
            String finalFileName = baseName + ".png";
            int counter = 1;
            while (Files.exists(jobDir.resolve(finalFileName))) {
                finalFileName = baseName + "_" + counter + ".png";
                counter++;
            }

            // 仅导出最终结果, 放在 jobDir 根目录下
            ImageSupport.writePng(processedImages.finalImage, jobDir.resolve(finalFileName).toFile());

            ProcessResultItem item = new ProcessResultItem();
            item.setFileName(finalFileName);
            item.setOriginalWidth(processedImages.originalImage.getWidth());
            item.setOriginalHeight(processedImages.originalImage.getHeight());
            item.setFinalUrl(toPublicUrl(jobId, finalFileName));
            
            response.getItems().add(item);
        }

        createZip(jobDir);
        response.setDownloadUrl("/api/images/jobs/" + jobId + "/download");
        return response;
    }

    private ProcessOptions resolveOptions(ProcessOptions options, List<ProcessOptions> optionsList, int index) {
        if (optionsList == null || index >= optionsList.size() || optionsList.get(index) == null) {
            return options;
        }
        return optionsList.get(index);
    }

    public Path resolveJobFile(String jobId, String fileName) {
        Path jobPath = jobPath(jobId);
        return safeResolve(jobPath, jobPath.resolve(fileName).normalize());
    }

    public Path resolveNestedFile(String jobId, String folder, String fileName) {
        Path jobPath = jobPath(jobId);
        return safeResolve(jobPath, jobPath.resolve(folder).resolve(fileName).normalize());
    }

    public Path resolveZip(String jobId) {
        Path jobPath = jobPath(jobId);
        return safeResolve(jobPath, jobPath.resolve("results.zip").normalize());
    }

    private ProcessedImages processImage(MultipartFile file, ProcessOptions options, ImageEditOptions editOptions) throws IOException {
        BufferedImage readImage = ImageIO.read(file.getInputStream());
        if (readImage == null) {
            throw new IOException("无法解析图片: " + file.getOriginalFilename());
        }

        BufferedImage originalImage = ImageSupport.toArgb(readImage);
        ImageEditOptions normalizedEditOptions = normalizeEditOptions(editOptions, originalImage);
        Rectangle cropRect = ImageSupport.clampRectangle(
                originalImage,
                normalizedEditOptions.getCropX(),
                normalizedEditOptions.getCropY(),
                normalizedEditOptions.getCropWidth(),
                normalizedEditOptions.getCropHeight()
        );

        BufferedImage croppedImage = ImageSupport.crop(originalImage, cropRect);
        BufferedImage eraseMask = buildEraseMask(normalizedEditOptions, originalImage, cropRect);
        normalizedEditOptions.setCropX(0);
        normalizedEditOptions.setCropY(0);
        normalizedEditOptions.setCropWidth(croppedImage.getWidth());
        normalizedEditOptions.setCropHeight(croppedImage.getHeight());

        ProcessingContext context = new ProcessingContext(options, normalizedEditOptions, originalImage, croppedImage);
        context.setEraseMask(eraseMask);
        context.setEstimatedBackground(ImageSupport.estimateBackground(croppedImage));

        for (ImageOperation operation : operations) {
            if (operation.supports(context)) {
                operation.apply(context);
            }
        }

        ProcessedImages processedImages = new ProcessedImages();
        processedImages.originalImage = originalImage;
        processedImages.croppedImage = context.getCroppedOriginalImage();
        processedImages.cleanedImage = context.getPreparedImage() != null ? context.getPreparedImage() : context.getCurrentImage();
        processedImages.finalImage = context.getFinalImage() != null ? context.getFinalImage() : context.getCurrentImage();
        processedImages.transparentImage = context.getTransparentImage();
        processedImages.maskImage = context.getMaskImage();
        return processedImages;
    }

    private ImageEditOptions resolveEditOptions(List<ImageEditOptions> edits, int index) {
        if (edits == null || index >= edits.size() || edits.get(index) == null) {
            return new ImageEditOptions();
        }
        return edits.get(index);
    }

    private ImageEditOptions normalizeEditOptions(ImageEditOptions source, BufferedImage originalImage) {
        ImageEditOptions target = source == null ? new ImageEditOptions() : source;
        Rectangle cropRect = ImageSupport.clampRectangle(
                originalImage,
                target.getCropX(),
                target.getCropY(),
                target.getCropWidth(),
                target.getCropHeight()
        );
        target.setCropX(cropRect.x);
        target.setCropY(cropRect.y);
        target.setCropWidth(cropRect.width);
        target.setCropHeight(cropRect.height);

        if (target.getSamplePoints() != null) {
            List<java.util.Map<String, Object>> normalizedPoints = new ArrayList<>();
            for (java.util.Map<String, Object> p : target.getSamplePoints()) {
                if (p.get("x") != null && p.get("y") != null) {
                    int px = ((Number) p.get("x")).intValue();
                    int py = ((Number) p.get("y")).intValue();
                    int nx = clamp(px, cropRect.x, cropRect.x + cropRect.width - 1) - cropRect.x;
                    int ny = clamp(py, cropRect.y, cropRect.y + cropRect.height - 1) - cropRect.y;
                    java.util.Map<String, Object> newP = new java.util.HashMap<>();
                    newP.put("x", nx);
                    newP.put("y", ny);
                    if (p.containsKey("color")) {
                        newP.put("color", p.get("color"));
                    }
                    normalizedPoints.add(newP);
                }
            }
            target.setSamplePoints(normalizedPoints);
        } else if (target.getSampleX() != null && target.getSampleY() != null) {
            int sampleX = clamp(target.getSampleX(), cropRect.x, cropRect.x + cropRect.width - 1) - cropRect.x;
            int sampleY = clamp(target.getSampleY(), cropRect.y, cropRect.y + cropRect.height - 1) - cropRect.y;
            target.setSampleX(sampleX);
            target.setSampleY(sampleY);
        }
        return target;
    }

    private BufferedImage buildEraseMask(ImageEditOptions editOptions, BufferedImage originalImage, Rectangle cropRect) throws IOException {
        if (editOptions == null || editOptions.getEraseMaskDataUrl() == null || editOptions.getEraseMaskDataUrl().trim().isEmpty()) {
            return null;
        }
        BufferedImage maskImage = ImageSupport.decodeDataUrl(editOptions.getEraseMaskDataUrl());
        if (maskImage.getWidth() == originalImage.getWidth() && maskImage.getHeight() == originalImage.getHeight()) {
            return ImageSupport.crop(maskImage, cropRect);
        }
        if (maskImage.getWidth() == cropRect.width && maskImage.getHeight() == cropRect.height) {
            return maskImage;
        }
        return ImageSupport.resizeNearest(maskImage, cropRect.width, cropRect.height);
    }

    private void createZip(Path jobDir) throws IOException {
        Path zipPath = jobDir.resolve("results.zip");
        try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            Files.walk(jobDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> !"results.zip".equalsIgnoreCase(path.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> addToZip(jobDir, path, outputStream));
        }
    }

    private void addToZip(Path jobDir, Path file, ZipOutputStream outputStream) {
        try {
            String entryName = jobDir.relativize(file).toString().replace("\\", "/");
            outputStream.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, outputStream);
            outputStream.closeEntry();
        } catch (IOException exception) {
            throw new IllegalStateException("压缩结果文件失败: " + file, exception);
        }
    }

    private int operationWeight(ImageOperation operation) {
        String name = operation.getClass().getSimpleName();
        if ("WatermarkCleanupOperation".equals(name)) {
            return 1;
        }
        if ("ManualEraseOperation".equals(name)) {
            return 2;
        }
        if ("BackgroundRemovalOperation".equals(name)) {
            return 3;
        }
        if ("PixelResizeOperation".equals(name)) {
            return 4;
        }
        return 100;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String sanitizeFileName(String originalFilename) {
        String name = originalFilename == null ? "image" : originalFilename;
        int extensionIndex = name.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? name.substring(0, extensionIndex) : name;
        return baseName.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}_\\-\\u4e00-\\u9fa5]+", "_");
    }

    private String toPublicUrl(String jobId, String fileName) {
        return "/api/images/jobs/" + jobId + "/files/" + fileName;
    }

    private String toPublicUrl(String jobId, String folder, String fileName) {
        return "/api/images/jobs/" + jobId + "/files/" + folder + "/" + fileName;
    }

    public byte[] buildSpriteSheetPng(String jobId, int cols, int gap, String preset) throws IOException {
        Path jobDir = jobPath(jobId);
        if (!Files.exists(jobDir) || !Files.isDirectory(jobDir)) {
            return null;
        }
        List<Path> frames = listJobPngFrames(jobDir);
        if (frames.isEmpty()) {
            return null;
        }

        int colsClamped = clamp(cols, 1, 50);
        int gapClamped = clamp(gap, 0, 128);
        int presetSize = parsePresetSize(preset);

        int cellW;
        int cellH;
        if (presetSize > 0) {
            cellW = presetSize;
            cellH = presetSize;
        } else {
            int maxW = 1;
            int maxH = 1;
            for (Path p : frames) {
                BufferedImage img = ImageIO.read(p.toFile());
                if (img == null) continue;
                maxW = Math.max(maxW, img.getWidth());
                maxH = Math.max(maxH, img.getHeight());
            }
            cellW = maxW;
            cellH = maxH;
        }

        int count = frames.size();
        int rows = (int) Math.ceil(count / (double) colsClamped);
        int sheetW = colsClamped * cellW + (colsClamped - 1) * gapClamped;
        int sheetH = rows * cellH + (rows - 1) * gapClamped;

        BufferedImage sheet = new BufferedImage(sheetW, sheetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
        try {
            for (int i = 0; i < count; i++) {
                Path p = frames.get(i);
                BufferedImage img = ImageIO.read(p.toFile());
                if (img == null) continue;
                int col = i % colsClamped;
                int row = i / colsClamped;
                int x = col * (cellW + gapClamped);
                int y = row * (cellH + gapClamped);
                if (presetSize > 0) {
                    int iw = img.getWidth();
                    int ih = img.getHeight();
                    double scale = Math.min(cellW / (double) iw, cellH / (double) ih);
                    int dw = Math.max(1, (int) Math.round(iw * scale));
                    int dh = Math.max(1, (int) Math.round(ih * scale));
                    int ox = x + (cellW - dw) / 2;
                    int oy = y + (cellH - dh) / 2;
                    g.drawImage(img, ox, oy, ox + dw, oy + dh, 0, 0, iw, ih, null);
                } else {
                    int ox = x + (cellW - img.getWidth()) / 2;
                    int oy = y + (cellH - img.getHeight()) / 2;
                    g.drawImage(img, ox, oy, null);
                }
            }
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(sheet, "png", out);
        return out.toByteArray();
    }

    public byte[] buildAnimatedGif(String jobId, int fps, String preset) throws IOException {
        Path jobDir = jobPath(jobId);
        if (!Files.exists(jobDir) || !Files.isDirectory(jobDir)) {
            return null;
        }
        List<Path> frames = listJobPngFrames(jobDir);
        if (frames.isEmpty()) {
            return null;
        }

        int fpsClamped = clamp(fps, 1, 60);
        int delayCs = Math.max(1, (int) Math.round(100.0 / fpsClamped));
        int presetSize = parsePresetSize(preset);

        int frameW;
        int frameH;
        if (presetSize > 0) {
            frameW = presetSize;
            frameH = presetSize;
        } else {
            int maxW = 1;
            int maxH = 1;
            for (Path p : frames) {
                BufferedImage img = safeReadImage(p);
                if (img == null) continue;
                maxW = Math.max(maxW, img.getWidth());
                maxH = Math.max(maxH, img.getHeight());
            }
            frameW = maxW;
            frameH = maxH;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            GifSequenceWriter writer = new GifSequenceWriter(ios, BufferedImage.TYPE_INT_ARGB, delayCs, true);
            for (Path p : frames) {
                BufferedImage img = safeReadImage(p);
                if (img == null) continue;
                BufferedImage frame = new BufferedImage(frameW, frameH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = frame.createGraphics();
                g.setComposite(AlphaComposite.Src);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
                try {
                    if (presetSize > 0) {
                        int iw = img.getWidth();
                        int ih = img.getHeight();
                        double scale = Math.min(frameW / (double) iw, frameH / (double) ih);
                        int dw = Math.max(1, (int) Math.round(iw * scale));
                        int dh = Math.max(1, (int) Math.round(ih * scale));
                        int ox = (frameW - dw) / 2;
                        int oy = (frameH - dh) / 2;
                        g.drawImage(img, ox, oy, ox + dw, oy + dh, 0, 0, iw, ih, null);
                    } else {
                        int ox = (frameW - img.getWidth()) / 2;
                        int oy = (frameH - img.getHeight()) / 2;
                        g.drawImage(img, ox, oy, null);
                    }
                } finally {
                    g.dispose();
                }
                writer.writeToSequence(frame);
            }
            writer.close();
        }
        return out.toByteArray();
    }

    public byte[] buildSpineZip(String jobId, int cols, int gap, int fps, String preset) throws IOException {
        Path jobDir = jobPath(jobId);
        if (!Files.exists(jobDir) || !Files.isDirectory(jobDir)) {
            return null;
        }
        List<Path> frames = listJobPngFrames(jobDir);
        if (frames.isEmpty()) {
            return null;
        }

        int colsClamped = clamp(cols, 1, 50);
        int gapClamped = clamp(gap, 0, 128);
        int fpsClamped = clamp(fps, 1, 60);
        int presetSize = parsePresetSize(preset);

        byte[] sheet = buildSpriteSheetPng(jobId, colsClamped, gapClamped, preset);
        if (sheet == null || sheet.length == 0) {
            return null;
        }

        int cellW;
        int cellH;
        if (presetSize > 0) {
            cellW = presetSize;
            cellH = presetSize;
        } else {
            int maxW = 1;
            int maxH = 1;
            for (Path p : frames) {
                BufferedImage img = ImageIO.read(p.toFile());
                if (img == null) continue;
                maxW = Math.max(maxW, img.getWidth());
                maxH = Math.max(maxH, img.getHeight());
            }
            cellW = maxW;
            cellH = maxH;
        }

        int count = frames.size();
        int rows = (int) Math.ceil(count / (double) colsClamped);
        int sheetW = colsClamped * cellW + (colsClamped - 1) * gapClamped;
        int sheetH = rows * cellH + (rows - 1) * gapClamped;

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fps", fpsClamped);
        meta.put("cols", colsClamped);
        meta.put("gap", gapClamped);
        meta.put("preset", preset == null ? "original" : preset);
        Map<String, Object> sheetMeta = new LinkedHashMap<>();
        sheetMeta.put("width", sheetW);
        sheetMeta.put("height", sheetH);
        sheetMeta.put("cellWidth", cellW);
        sheetMeta.put("cellHeight", cellH);
        meta.put("sheet", sheetMeta);
        List<Map<String, Object>> frameList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int col = i % colsClamped;
            int row = i / colsClamped;
            int x = col * (cellW + gapClamped);
            int y = row * (cellH + gapClamped);
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("index", i);
            f.put("name", frames.get(i).getFileName().toString());
            f.put("x", x);
            f.put("y", y);
            f.put("w", cellW);
            f.put("h", cellH);
            frameList.add(f);
        }
        meta.put("frames", frameList);

        byte[] metaJson = objectMapper.writeValueAsBytes(meta);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            ZipEntry sheetEntry = new ZipEntry("sheet.png");
            zos.putNextEntry(sheetEntry);
            zos.write(sheet);
            zos.closeEntry();

            ZipEntry metaEntry = new ZipEntry("meta.json");
            zos.putNextEntry(metaEntry);
            zos.write(metaJson);
            zos.closeEntry();

            for (Path p : frames) {
                ZipEntry frameEntry = new ZipEntry("frames/" + p.getFileName().toString());
                zos.putNextEntry(frameEntry);
                zos.write(Files.readAllBytes(p));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private List<Path> listJobPngFrames(Path jobDir) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.list(jobDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    private BufferedImage safeReadImage(Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (Exception e) {
            return null;
        }
    }

    private int parsePresetSize(String preset) {
        if (preset == null) return -1;
        String p = preset.trim();
        if (p.isEmpty() || "original".equalsIgnoreCase(p)) return -1;
        try {
            int v = Integer.parseInt(p);
            if (v <= 0) return -1;
            return clamp(v, 1, 2048);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Path jobPath(String jobId) {
        return safeResolve(storageRoot, storageRoot.resolve(jobId).normalize());
    }

    private Path safeResolve(Path root, Path candidate) {
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("非法路径访问");
        }
        return candidate;
    }

    private static class ProcessedImages {
        private BufferedImage originalImage;
        private BufferedImage croppedImage;
        private BufferedImage cleanedImage;
        private BufferedImage transparentImage;
        private BufferedImage maskImage;
        private BufferedImage finalImage;
    }

    private static class GifSequenceWriter {
        private final ImageWriter gifWriter;
        private final ImageWriteParam imageWriteParam;
        private final int imageType;
        private final int delayCs;
        private final boolean loop;

        GifSequenceWriter(ImageOutputStream outputStream, int imageType, int delayCs, boolean loop) throws IOException {
            java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
            if (writers == null || !writers.hasNext()) {
                throw new IllegalStateException("当前JRE不支持GIF写入");
            }
            gifWriter = writers.next();
            imageWriteParam = gifWriter.getDefaultWriteParam();
            this.imageType = imageType;
            this.delayCs = Math.max(1, delayCs);
            this.loop = loop;
            IIOMetadata streamMetaData = gifWriter.getDefaultStreamMetadata(imageWriteParam);

            IIOMetadata effectiveStreamMeta = streamMetaData;
            if (loop && effectiveStreamMeta != null) {
                try {
                    String streamFormatName = effectiveStreamMeta.getNativeMetadataFormatName();
                    if (streamFormatName != null && streamFormatName.toLowerCase().contains("gif")) {
                        IIOMetadataNode streamRoot = (IIOMetadataNode) effectiveStreamMeta.getAsTree(streamFormatName);
                        IIOMetadataNode appExtensions = getNode(streamRoot, "ApplicationExtensions");
                        IIOMetadataNode appNode = new IIOMetadataNode("ApplicationExtension");
                        appNode.setAttribute("applicationID", "NETSCAPE");
                        appNode.setAttribute("authenticationCode", "2.0");
                        int loopCount = 0;
                        appNode.setUserObject(new byte[]{0x1, (byte) (loopCount & 0xFF), (byte) ((loopCount >> 8) & 0xFF)});
                        appExtensions.appendChild(appNode);
                        effectiveStreamMeta.setFromTree(streamFormatName, streamRoot);
                    }
                } catch (Exception ignored) {
                    effectiveStreamMeta = null;
                }
            }

            gifWriter.setOutput(outputStream);
            gifWriter.prepareWriteSequence(effectiveStreamMeta);
        }

        void writeToSequence(BufferedImage img) throws IOException {
            IIOMetadata meta = buildFrameMetadata();
            gifWriter.writeToSequence(new IIOImage(img, null, meta), imageWriteParam);
        }

        void close() throws IOException {
            gifWriter.endWriteSequence();
        }

        private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
            int nNodes = rootNode.getLength();
            for (int i = 0; i < nNodes; i++) {
                if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                    return (IIOMetadataNode) rootNode.item(i);
                }
            }
            IIOMetadataNode node = new IIOMetadataNode(nodeName);
            rootNode.appendChild(node);
            return node;
        }

        private IIOMetadata buildFrameMetadata() {
            try {
                ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);
                IIOMetadata meta = gifWriter.getDefaultImageMetadata(imageTypeSpecifier, imageWriteParam);
                if (meta == null) return null;
                String metaFormatName = meta.getNativeMetadataFormatName();
                if (metaFormatName == null || !metaFormatName.toLowerCase().contains("gif")) return meta;
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(metaFormatName);
                IIOMetadataNode gce = getNode(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "restoreToBackgroundColor");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("delayTime", Integer.toString(delayCs));
                meta.setFromTree(metaFormatName, root);
                return meta;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
