/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.poifs.crypt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

import org.apache.poi.openxml4j.util.ZipEntrySource;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.TempFile;

public class AesZipFileZipEntrySource implements ZipEntrySource {
    final File tmpFile;
    final ZipFile zipFile;
    final Cipher ci;
    boolean closed;

    public AesZipFileZipEntrySource(File tmpFile, Cipher ci) throws IOException {
        this.tmpFile = tmpFile;
        this.zipFile = new ZipFile(tmpFile);
        this.ci = ci;
        this.closed = false;
    }

    /**
     * Note: the file sizes are rounded up to the next cipher block size,
     * so don't rely on file sizes of these custom encrypted zip file entries!
     */
    public Enumeration<? extends ZipEntry> getEntries() {
        return zipFile.entries();
    }

    @SuppressWarnings("resource")
    public InputStream getInputStream(ZipEntry entry) throws IOException {
        InputStream is = zipFile.getInputStream(entry);
        return new CipherInputStream(is, ci);
    }

    @Override
    public void close() throws IOException {
        if(!closed) {
            zipFile.close();
            tmpFile.delete();
        }
        closed = true;
    }
    
    @Override
    public boolean isClosed() {
        return closed;
    }
    
    public static ZipEntrySource createZipEntrySource(InputStream is) throws IOException, GeneralSecurityException {
        // generate session key
        SecureRandom sr = new SecureRandom();
        byte[] ivBytes = new byte[16], keyBytes = new byte[16];
        sr.nextBytes(ivBytes);
        sr.nextBytes(keyBytes);
        final File tmpFile = TempFile.createTempFile("protectedXlsx", ".zip");
        copyToFile(is, tmpFile, CipherAlgorithm.aes128, keyBytes, ivBytes);
        IOUtils.closeQuietly(is);
        return fileToSource(tmpFile, CipherAlgorithm.aes128, keyBytes, ivBytes);
    }
    
    private static void copyToFile(InputStream is, File tmpFile, CipherAlgorithm cipherAlgorithm, byte keyBytes[], byte ivBytes[]) throws IOException, GeneralSecurityException {
        SecretKeySpec skeySpec = new SecretKeySpec(keyBytes, cipherAlgorithm.jceId);
        Cipher ciEnc = CryptoFunctions.getCipher(skeySpec, cipherAlgorithm, ChainingMode.cbc, ivBytes, Cipher.ENCRYPT_MODE, "PKCS5Padding");

        ZipInputStream zis = new ZipInputStream(is);
        FileOutputStream fos = new FileOutputStream(tmpFile);
        ZipOutputStream zos = new ZipOutputStream(fos);

        ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            // the cipher output stream pads the data, therefore we can't reuse the ZipEntry with set sizes
            // as those will be validated upon close()
            ZipEntry zeNew = new ZipEntry(ze.getName());
            zeNew.setComment(ze.getComment());
            zeNew.setExtra(ze.getExtra());
            zeNew.setTime(ze.getTime());
            // zeNew.setMethod(ze.getMethod());
            zos.putNextEntry(zeNew);
            FilterOutputStream fos2 = new FilterOutputStream(zos){
                // don't close underlying ZipOutputStream
                public void close() {}
            };
            CipherOutputStream cos = new CipherOutputStream(fos2, ciEnc);
            IOUtils.copy(zis, cos);
            cos.close();
            fos2.close();
            zos.closeEntry();
            zis.closeEntry();
        }
        zos.close();
        fos.close();
        zis.close();
    }
    
    private static ZipEntrySource fileToSource(File tmpFile, CipherAlgorithm cipherAlgorithm, byte keyBytes[], byte ivBytes[]) throws ZipException, IOException {
        SecretKeySpec skeySpec = new SecretKeySpec(keyBytes, cipherAlgorithm.jceId);
        Cipher ciDec = CryptoFunctions.getCipher(skeySpec, cipherAlgorithm, ChainingMode.cbc, ivBytes, Cipher.DECRYPT_MODE, "PKCS5Padding");
        return new AesZipFileZipEntrySource(tmpFile, ciDec);
    }

}

