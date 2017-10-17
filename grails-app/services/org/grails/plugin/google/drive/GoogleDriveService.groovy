/*
 * Copyright 2013 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugin.google.drive

import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import org.apache.commons.fileupload.FileItem
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.commons.CommonsMultipartFile

import javax.annotation.PostConstruct

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
class GoogleDriveService {

    def grailsApplication

    GoogleDrive drive

    @PostConstruct
    def init() {
        def config = grailsApplication.config.google.drive


        if (config.enabled) {
            switch (config.credentials.type) {
                case 'web':
                    drive = getWebConfiguredDrive(config.credentials.filePath)
                    break
                case 'service':
                    drive = getServiceConfiguredDrive(config.json)
                    break
            }
        }
    }

    private def getWebConfiguredDrive(String configFilePath) {
        def config = grailsApplication.config.google.drive
        String key
        String secret
        def jsonConfig = null

        try {
            def jsonFile = new java.io.File(configFilePath)
            jsonConfig = JSONConfigLoader.getConfigFromJSON('web', jsonFile)
        } catch (IOException e) {
            log.error e.message
        }

        key = jsonConfig?.key ?: config.key
        if (!key) {
            throw new RuntimeException('Google Drive API key is not specified')
        }

        secret = jsonConfig?.secret ?: config.secret
        if (!secret) {
            throw new RuntimeException('Google Drive API secret is not specified')
        }

        return new GoogleDrive(key, secret, (String) config.credentials.path, 'grails')
    }

    private def getServiceConfiguredDrive(json) {
        def config = grailsApplication.config.google.drive
        def key
        def secret

        key = json?.client_email ?: config.key
        if (!key) {
            throw new RuntimeException('Google Drive API email is not specified for service account')
        }

        secret = json?.private_key ?: config.secret
        if (!secret) {
            throw new RuntimeException('Google Drive API private key is not specified for service account')
        }

        return new GoogleDrive(key, secret, config.scopes)
    }

    List<File> list() {
        def files = drive.native.files().list().execute()

        files.getItems()
    }

    List<File> foldersList() {
        GoogleDrive.foldersList(drive.native).getItems()
    }

    File uploadFile(java.io.File file, String parentFolderName = null, Boolean convert = false) {
        drive.uploadFile(file, parentFolderName, convert)
    }

    File uploadFile(MultipartFile multipartFile, String parentFolderName = null,  Boolean convert = false) {
        drive.uploadFile(multipartFile, parentFolderName, convert)
    }

    File uploadFileByFolderId(MultipartFile multipartFile, String folderId = null, Boolean convert) {
        drive.uploadFileByFolderId(multipartFile, folderId, convert)
    }

    void uploadFileByFolderIdUsingMultipart(String fileName, byte[] dataToInsert, String folderId, String contentType = "text/csv", Boolean convert){

        java.io.File file = java.io.File.createTempFile("tmp",".csv")

        FileItem fileItem = new DiskFileItemFactory().createItem("tmp/temporal", contentType, true, fileName)

        fileItem.getOutputStream().write(dataToInsert)

        MultipartFile multipartFile = new CommonsMultipartFile(fileItem)

        this.uploadFileByFolderId(multipartFile, folderId, convert)

        file.delete()
    }

    File makeDirectory(String name) {
        GoogleDrive.insertFolder(drive.native, name, false)
    }

    def insertPermission(String fileId, String role, String type) {
        def permission = new Permission()
                .setRole(role)
                .setType(type)

        drive.native.permissions().insert(fileId, permission).execute()
    }

    def remove(String id) {
        drive.native.files().delete(id).execute()
    }

    void replaceFile(String name, String folderId, String content, String contentType, Boolean convert){

        String replaceContentType = ""
        if (contentType == "text/csv")
            replaceContentType = "application/vnd.google-apps.spreadsheet"

        try{
            List<String> filesIds = drive.native.files().list().setQ("\"$folderId\" in parents and mimeType=\"$replaceContentType\"").execute().get("items").findAll({it.title == "$name"})*.get("id")
            filesIds.each {remove(it)}

        }catch(Exception ignore){}

        uploadFileByFolderIdUsingMultipart(name, content.bytes, folderId, contentType, convert)

    }

}
