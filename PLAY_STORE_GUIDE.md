# Google Play Store Deployment Guide

This guide provides step-by-step instructions on how to configure your Google Play Store account and GitHub repository to enable automated deployments using the provided GitHub Actions workflow.

## Step 1: Google Play Console Setup

### 1. Enable the Google Play Android Developer API
- Go to the [Google Play Console](https://play.google.com/console).
- In the left-hand menu, navigate to **Setup > API access**.
- Click **Choose a project to link** and select the Google Cloud project you want to link. If you don't have one, you can create a new one.
- The **Google Play Android Developer API** should be automatically enabled. If not, you can enable it from the [Google Cloud Console](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com).

### 2. Create a Service Account
- From the **API access** page in the Google Play Console, scroll down to the **Service accounts** section.
- Click **Create new service account**.
- Follow the on-screen instructions to create a service account. You will be redirected to the Google Cloud Platform.
- When creating the service account, assign it the **Service Account User** role.
- After creating the service account, you will need to create a key. Choose **JSON** as the key type and download the file. This file contains the credentials needed to authenticate with the Google Play API.

### 3. Grant Permissions in Play Console
- Go back to the **API access** page in the Google Play Console.
- Find the newly created service account in the list and click **Grant access**.
- Grant the following permissions:
  - **Admin (all permissions)**: This is the simplest option, but you can also grant more granular permissions if needed.
- Click **Invite user** to save the changes.

## Step 2: GitHub Secrets Configuration

You need to add the following secrets to your GitHub repository to allow the workflow to access the Play Store and sign the release.

- Go to your repository's **Settings** tab.
- In the left-hand menu, navigate to **Secrets and variables > Actions**.
- Click **New repository secret** for each of the following secrets:

1.  **`SERVICE_ACCOUNT_JSON`**:
    -   Open the JSON file you downloaded when creating the service account.
    -   Copy the entire content of the file and paste it into the secret's value field.

2.  **`SIGNING_KEY`**:
    -   This is your application's signing key, base64-encoded. If you don't have a signing key, you can generate one using the following command:
        ```bash
        keytool -genkey -v -keystore my-release-key.keystore -alias my-alias -keyalg RSA -keysize 2048 -validity 10000
        ```
    -   Once you have the keystore file, you need to base64-encode it. You can do this with the following command:
        ```bash
        openssl base64 < my-release-key.keystore | tr -d '\n' | tee my-release-key.keystore.base64.txt
        ```
    -   Copy the output and paste it into the secret's value field.

3.  **`ALIAS`**:
    -   The alias of your signing key (e.g., `my-alias`).

4.  **`KEY_STORE_PASSWORD`**:
    -   The password for your keystore.

5.  **`KEY_PASSWORD`**:
    -   The password for your key.

## Conclusion

Once you have completed these steps, the GitHub Actions workflow will be able to automatically build, sign, and deploy your Android application to the Google Play Store whenever you push to the `main` branch.
