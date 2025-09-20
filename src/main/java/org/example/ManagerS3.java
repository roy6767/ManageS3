package org.example;

import java.util.List;
import java.util.Scanner;

public class ManagerS3 {
    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            S3Actions s3Actions = new S3Actions();
            String bucketName = null;

            boolean running = true;
            while (running) {
                System.out.println("\n--- S3 File Manager Menu ---");
                System.out.println("Current Bucket: " + (bucketName != null ? bucketName : "Not Selected"));
                System.out.println("Enter your choice (1-8):");
                System.out.println("1. List All files from S3 bucket");
                System.out.println("2. Upload a file to S3 bucket");
                System.out.println("3. Download a file from S3 bucket");
                System.out.println("4. Delete a file from S3 bucket");
                System.out.println("5. Search for a file in S3 bucket");
                System.out.println("6. Choose the S3 bucket");
                System.out.println("7. Upload a folder to S3 bucket");
                System.out.println("8. Exit the program");
                System.out.println("-----------------------------------");
                System.out.print("Enter your choice: ");

                try {
                    int choice = sc.nextInt();
                    sc.nextLine();

                    if (choice != 6 && choice != 8 && bucketName == null) {
                        System.out.println("Please select a bucket first (Option 6).");
                        continue;
                    }

                    switch (choice) {
                        case 1:
                            System.out.println("Listing files...");
                            List<String> files = s3Actions.listAllFiles();
                            if (files.isEmpty()) {
                                System.out.println("No files found or bucket is empty.");
                            } else {
                                files.forEach(System.out::println);
                            }
                            break;
                        case 2:
                            System.out.println(s3Actions.uploadFile(sc));
                            break;
                        case 3:
                            System.out.println(s3Actions.downloadFile(sc));
                            break;
                        case 4:
                            System.out.println(s3Actions.deleteAFile(sc));
                            break;
                        case 5:
                            System.out.println("Searching files...");
                            List<String> searchResults = s3Actions.searchAFiles(sc);
                            if (searchResults.isEmpty()) {
                                System.out.println("No files found with that prefix.");
                            } else {
                                System.out.println("Found " + searchResults.size() + " file(s):");
                                searchResults.forEach(System.out::println);
                            }
                            break;
                        case 6:
                            System.out.print("Please enter the S3 bucket name: ");
                            bucketName = sc.nextLine();
                            try {
                                s3Actions.chooseBucket(bucketName);
                                System.out.println("Bucket set to: " + bucketName);
                            } catch (Exception e) {
                                System.out.println("Failed to select bucket: " + e.getMessage());
                                bucketName = null;
                            }
                            break;
                        case 7:
                            System.out.println(s3Actions.uploadFolder(sc));
                            break;
                        case 8:
                            System.out.println("Exiting the program. Goodbye!");
                            running = false;
                            break;
                        default:
                            System.out.println("Invalid choice. Please enter a number from 1 to 8.");
                            break;
                    }
                } catch (java.util.InputMismatchException e) {
                    System.out.println("Invalid input. Please enter a number from 1 to 8.");
                    sc.nextLine(); // clear invalid input
                }
            }
            s3Actions.close();
        }
    }
}
