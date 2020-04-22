package me.bob.signservice.pdf;

/**
 * @author Bob
 * @create 2020-03-12
 * @since 1.0.0
 */

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.source.RASInputStream;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.element.Image;
import com.itextpdf.signatures.*;
import com.itextpdf.signatures.PdfSignatureAppearance.RenderingMode;
import com.itextpdf.signatures.PdfSigner.CryptoStandard;
import me.bob.signservice.entity.PdfSignInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.swing.*;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class SignPdfUtil {
    public static final String KEYSTORE = "/Users/***/Desktop/测试数据/9582.pfx";//keystore文件路径
    public static final char[] PASSWORD = "408871".toCharArray();    // keystore密码
    public static final String SRC = "/Users/***/Desktop/测试数据/testPdf.pdf";//需要盖章的pdf文件路径
    public static final String DEST = "/Users/***/Desktop/测试数据/testPdf11.pdf";//盖章后生产的pdf文件路径
    public static final String stamperSrc = "/Users/***/Desktop/测试数据/testSeal.png";//印章路径

    public static  void   sign(String src  //需要签章的pdf文件路径
            , String dest  // 签完章的pdf文件路径
            , Certificate[] chain //证书链
            , PrivateKey pk //签名私钥
            , String digestAlgorithm  //摘要算法名称，例如SHA-1
            , String provider  // 密钥算法提供者，可以为null
            , CryptoStandard subfilter //数字签名格式，itext有2种
            , String reason  //签名的原因，显示在pdf签名属性中，随便填
            , String location) //签名的地点，显示在pdf签名属性中，随便填
            throws GeneralSecurityException, IOException {
        //下边的步骤都是固定的，照着写就行了，没啥要解释的
        PdfReader reader = new PdfReader(src);
        PdfDocument document = new PdfDocument(reader);
        document.setDefaultPageSize(PageSize.TABLOID);
        //目标文件输出流
        FileOutputStream os = new FileOutputStream(dest);
        //创建签章工具PdfSigner ，最后一个boolean参数
        //false的话，pdf文件只允许被签名一次，多次签名，最后一次有效
        //true的话，pdf可以被追加签名，验签工具可以识别出每次签名之后文档是否被修改
        PdfSigner stamper = new PdfSigner(reader, os, true);
        // 获取数字签章属性对象，设定数字签章的属性
        PdfSignatureAppearance appearance = stamper.getSignatureAppearance();
        appearance.setReason(reason);
        appearance.setLocation(location);
        ImageData img = ImageDataFactory.create(stamperSrc);
        //读取图章图片，这个image是itext包的image
        Image image = new Image(img);
        float height = image.getImageHeight();
        float width = image.getImageWidth();
        //设置签名的位置，页码，签名域名称，多次追加签名的时候，签名与名称不能一样
        //签名的位置，是图章相对于pdf页面的位置坐标，原点为pdf页面左下角
        //四个参数的分别是，图章左下角x，图章左下角y，图章宽度，图章高度
        appearance.setPageNumber(1);
        appearance.setPageRect(new Rectangle(350, 100, width, height));
        //插入盖章图片
        appearance.setSignatureGraphic(img);
        //设置图章的显示方式，如下选择的是只显示图章（还有其他的模式，可以图章和签名描述一同显示）
        appearance.setRenderingMode(RenderingMode.GRAPHIC);
        // 这里的itext提供了2个用于签名的接口，可以自己实现，后边着重说这个实现
        // 摘要算法
        IExternalDigest digest = new BouncyCastleDigest();
                BouncyCastleProvider bouncyCastleProvider = new BouncyCastleProvider();
                Security.addProvider(bouncyCastleProvider);
        // 签名算法
        IExternalSignature signature = new PrivateKeySignature(pk, digestAlgorithm,bouncyCastleProvider.getName());
        // 调用itext签名方法完成pdf签章
        stamper.setCertificationLevel(1);
        stamper.signDetached(digest,signature, chain, null, null, null, 0, CryptoStandard.CADES);
    }

    /**
     * 解析返回签名信息
     * @param pdf
     * @return
     */
    public List<PdfSignInfo> getPdfSignInfo(byte[] pdf){

        //添加BC库支持
        BouncyCastleProvider provider = new BouncyCastleProvider();
        Security.addProvider(provider);

        List<PdfSignInfo> signInfoList = new ArrayList<>();

        try {
            PdfReader  pdfReader = new PdfReader(new ByteArrayInputStream(pdf));
            PdfDocument pdfDocument = new PdfDocument(pdfReader);

            SignatureUtil signatureUtil = new SignatureUtil(pdfDocument);

            List<String> signedNames = signatureUtil.getSignatureNames();

            //遍历签名信息
            for (String signedName : signedNames) {

                PdfSignInfo pdfSignInfo = new PdfSignInfo();
                pdfSignInfo.setSignatureName(signedName);
                pdfSignInfo.setRevisionNumber(signatureUtil.getRevision(signedName));

                PdfPKCS7 pdfPKCS7 = signatureUtil.verifySignature(signedName , "BC");

                pdfSignInfo.setSignDate(pdfPKCS7.getSignDate().getTime());
                pdfSignInfo.setDigestAlgorithm(pdfPKCS7.getDigestAlgorithm());
                pdfSignInfo.setLocation(pdfPKCS7.getLocation());
                pdfSignInfo.setReason(pdfPKCS7.getReason());
                pdfSignInfo.setEncryptionAlgorithm(pdfPKCS7.getEncryptionAlgorithm());

                X509Certificate signCert = pdfPKCS7.getSigningCertificate();

                pdfSignInfo.setSignerName(CertificateInfo.getSubjectFields(signCert).getField("CN"));

                PdfDictionary sigDict = signatureUtil.getSignatureDictionary(signedName);
                PdfString contactInfo = sigDict.getAsString(PdfName.ContactInfo);
                if (contactInfo != null) {
                    pdfSignInfo.setContactInfo(contactInfo.toString());
                }

                signInfoList.add(pdfSignInfo);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return signInfoList;

    }

    /**
     * 获取签名数据
     * @param signatureUtil
     * @param signedName
     * @return
     */
    private byte[] getSignData(SignatureUtil signatureUtil, String signedName) {
        PdfDictionary pdfDictionary = signatureUtil.getSignatureDictionary(signedName);
        PdfString contents = pdfDictionary.getAsString(PdfName.Contents);
        return contents.getValueBytes();
    }

    /**
     * 获取源数据（如果subFilter使用的是Adbe.pkcs7.detached就需要在验签的时候获取 源数据 并与 签名数据 进行 p7detach 校验）
     * @param pdfReader
     * @param signatureUtil
     * @param signedName
     * @return
     */
    private byte[] getOriginData(PdfReader pdfReader, SignatureUtil signatureUtil, String signedName) {

        byte[] originData = null;

        try {
            PdfSignature pdfSignature = signatureUtil.getSignature(signedName);
            PdfArray pdfArray = pdfSignature.getByteRange();
            RandomAccessFileOrArray randomAccessFileOrArray = pdfReader.getSafeFile();
            InputStream rg = new RASInputStream(new RandomAccessSourceFactory().createRanged(randomAccessFileOrArray.createSourceView(), SignatureUtil.asLongArray(pdfArray)));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n = 0;
            while (-1 != (n = rg.read(buf))) {
                outputStream.write(buf, 0, n);
            }

            originData = outputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return originData;

    }

    /**
     * 验签pdf
     *
     * @param pdf 签名好的pdf
     * @return 验签结果 true/false
     */
//    public boolean verifyPdf(byte[] pdf) {
//
//        boolean result = false;
//
//        try {
//            PdfReader pdfReader = new PdfReader(new ByteArrayInputStream(pdf));
//            PdfDocument pdfDocument = new PdfDocument(pdfReader);
//            SignatureUtil signatureUtil = new SignatureUtil(pdfDocument);
//            List<String> signedNames = signatureUtil.getSignatureNames();
//
//            //遍历签名的内容并做验签
//            for (String signedName : signedNames) {
//
//                //获取源数据
//                byte[] originData = getOriginData(pdfReader, signatureUtil, signedName);
//
//                //获取签名值
//                byte[] signedData = getSignData(signatureUtil , signedName);
//
//                //校验签名
//                result = SignUtil.verifyP7DetachData(originData , signedData);
//
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        return result;
//    }

    public String verifySignature(String pdfPath) throws IOException {

//        PrintWriter printWriter = new PrintWriter(pdfPath);
        PdfReader pdfReader = new PdfReader(new FileInputStream(pdfPath));
        PdfDocument pdfDocument = new PdfDocument(pdfReader);
        SignatureUtil signatureUtil = new SignatureUtil(pdfDocument);
        List<String> signedNames = signatureUtil.getSignatureNames();

        //遍历签名的内容并做验签
        for (String signedName : signedNames) {

            //获取源数据
            byte[] originData = getOriginData(pdfReader, signatureUtil, signedName);

            //获取签名值
            byte[] signedData = getSignData(signatureUtil, signedName);

            //校验签名

        }

        return "";


    }





    public static void main(String[] args) {
        try {
            // 读取keystore ，获得私钥和证书链 jks
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new FileInputStream(KEYSTORE), PASSWORD);
            String alias = (String) ks.aliases().nextElement();
            PrivateKey pk = (PrivateKey) ks.getKey(alias, PASSWORD);
            Certificate[] chain = ks.getCertificateChain(alias);
            // new一个上边自定义的方法对象，调用签名方法
            SignPdfUtil app = new SignPdfUtil();
            app.sign(SRC, String.format(DEST, 1), chain, pk, DigestAlgorithms.SHA256, null, CryptoStandard.CADES, "数字签名，不可否认",
                    "易云章平台");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
            e.printStackTrace();
        }
    }
}
