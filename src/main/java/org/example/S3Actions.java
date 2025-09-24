package org.example;

import io.github.cdimascio.dotenv.Dotenv;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class S3Actions {

    private final S3Client s3Client;
    private String bucketName;

    public S3Actions() {
        AwsCredentialsProvider credentialsProvider;
        String region;

        try {
            Dotenv dotenv = Dotenv.load();

            String accessKey = dotenv.get("AWS_ACCESS_KEY");
            String secretKey = dotenv.get("AWS_SECRET_KEY");
            region = dotenv.get("AWS_REGION", "eu-north-1");

            if (accessKey != null && secretKey != null) {
                credentialsProvider = () -> AwsBasicCredentials.create(accessKey, secretKey);
            } else {
                credentialsProvider = DefaultCredentialsProvider.create();
            }

        } catch (Exception e) {
            credentialsProvider = DefaultCredentialsProvider.create();
            region = "eu-north-1"; // default region
        }

        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    public void chooseBucket(String bucketName) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            this.bucketName = bucketName;
        } catch (S3Exception e) {
            throw new IllegalArgumentException("Bucket does not exist or is inaccessible: " + bucketName, e);
        }
    }

    private void checkBucketSelected() {
        if (bucketName == null) {
            throw new IllegalStateException("No bucket selected. Please select a bucket first.");
        }
    }

    public List<String> listAllFiles() {
        checkBucketSelected();
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).build();
            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            return response.contents().stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
        } catch (S3Exception e) {
            System.err.println("Failed to list files: " + e.awsErrorDetails().errorMessage());
            return List.of();
        }
    }

    public String uploadFile(Scanner sc) {
        checkBucketSelected();
        System.out.println("Enter the full path of the file to upload:");
        String inputPath = sc.nextLine().trim();

        // Handle quoted paths like "C:\Users\me\file.txt"
        if (inputPath.startsWith("\"") && inputPath.endsWith("\"")) {
            inputPath = inputPath.substring(1, inputPath.length() - 1);
        }

        File fileToUpload = new File(inputPath);
        if (!fileToUpload.exists() || !fileToUpload.isFile()) {
            return "Invalid file path. Upload cancelled.";
        }

        String fileKey = fileToUpload.getName();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromFile(fileToUpload));
            return "Successfully uploaded " + fileKey + " to " + bucketName;
        } catch (S3Exception e) {
            return "Failed to upload file: " + e.awsErrorDetails().errorMessage();
        }
    }

    public String downloadFile(Scanner sc) {
        checkBucketSelected();
        String fileKey = getFileKeyFromUser(sc);
        if (fileKey == null) {
            return "Download cancelled.";
        }

        System.out.println("Enter the directory path to save the downloaded file:");
        File selectedDirectory = new File(sc.nextLine().trim());
        if (!selectedDirectory.exists() || !selectedDirectory.isDirectory()) {
            return "Invalid directory path. Download cancelled.";
        }

        Path targetPath = Paths.get(selectedDirectory.getAbsolutePath(), fileKey);

        // Auto-rename if file already exists
        if (Files.exists(targetPath)) {
            String baseName = fileKey;
            String extension = "";
            int dotIndex = fileKey.lastIndexOf('.');
            if (dotIndex != -1) {
                baseName = fileKey.substring(0, dotIndex);
                extension = fileKey.substring(dotIndex);
            }

            int counter = 1;
            while (Files.exists(targetPath)) {
                String newFileName = baseName + "(" + counter + ")" + extension;
                targetPath = Paths.get(selectedDirectory.getAbsolutePath(), newFileName);
                counter++;
            }
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();

        try {
            s3Client.getObject(request, targetPath);
            return "Successfully downloaded " + fileKey + " to " + targetPath;
        } catch (S3Exception e) {
            return "Failed to download file: " + e.getMessage();
        }
    }

    public String deleteAFile(Scanner sc) {
        checkBucketSelected();
        String fileKey = getFileKeyFromUser(sc);
        if (fileKey == null) {
            return "Deletion cancelled.";
        }

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();

        try {
            s3Client.deleteObject(request);
            return "Successfully deleted " + fileKey;
        } catch (S3Exception e) {
            return "Failed to delete file: " + e.awsErrorDetails().errorMessage();
        }
    }

    public List<String> searchAFiles(Scanner sc) {
        checkBucketSelected();
        System.out.print("Enter the search keyword: ");
        String keyword = sc.nextLine().trim();

        if (keyword.isEmpty()) {
            System.err.println("Keyword cannot be empty. Search cancelled.");
            return List.of();
        }

        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();
            ListObjectsV2Response response = s3Client.listObjectsV2(request);

            return response.contents().stream()
                    .map(S3Object::key)
                    .filter(key -> key.toLowerCase().contains(keyword.toLowerCase()))
                    .toList();
        } catch (S3Exception e) {
            System.err.println("Failed to search files: " + e.awsErrorDetails().errorMessage());
            return List.of();
        }
    }

    public String uploadFolder(Scanner sc) {
        checkBucketSelected();
        System.out.println("Enter the full path of the folder to upload:");
        String inputPath = sc.nextLine().trim();

        // Handle quoted paths like "C:\Users\me\file.txt"
        if (inputPath.startsWith("\"") && inputPath.endsWith("\"")) {
            inputPath = inputPath.substring(1, inputPath.length() - 1);
        }

        File folderToUpload = new File(inputPath);

        if (!folderToUpload.exists() || !folderToUpload.isDirectory()) {
            return "Folder not found or is not a directory. Upload cancelled.";
        }

        Path sourcePath = folderToUpload.toPath();
        String zipFileName = folderToUpload.getName() + ".zip";
        Path zipFilePath = Paths.get(System.getProperty("java.io.tmpdir"), zipFileName);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
            Files.walk(sourcePath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        String entryName = sourcePath.relativize(path).toString().replace("\\", "/");
                        ZipEntry zipEntry = new ZipEntry(entryName);
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to zip file: " + path, e);
                        }
                    });
        } catch (IOException e) {
            return "Failed to compress folder: " + e.getMessage();
        }

        String fileKey = zipFileName;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromFile(zipFilePath.toFile()));
            return "Successfully uploaded " + fileKey + " to " + bucketName;
        } catch (S3Exception e) {
            return "Failed to upload file: " + e.awsErrorDetails().errorMessage();
        } finally {
            try {
                Files.deleteIfExists(zipFilePath);
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete temporary zip file: " + zipFilePath);
            }
        }
    }

    private String getFileKeyFromUser(Scanner sc) {
        List<String> files = listAllFiles();
        if (files.isEmpty()) {
            System.out.println("No files found in the bucket.");
            return null;
        }

        System.out.println("Available files in bucket '" + bucketName + "':");
        files.forEach(System.out::println);

        System.out.print("\nEnter the name of the file: ");
        String fileKey = sc.nextLine();

        if (fileKey.isEmpty()) {
            System.err.println("File key cannot be empty. Operation cancelled.");
            return null;
        }
        if (!files.contains(fileKey)) {
            System.err.println("The specified file does not exist in the bucket. Operation cancelled.");
            return null;
        }
        return fileKey;
    }

    public void close() {
        s3Client.close();
    }
}
