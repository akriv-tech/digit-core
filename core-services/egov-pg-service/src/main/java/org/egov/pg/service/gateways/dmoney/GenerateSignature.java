package org.egov.pg.service.gateways.dmoney;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class GenerateSignature {

	private static Map<String, Object> createOrderMap() {
		Map<String, Object> bizContent = Map.of(DMoneyConstants.MERCH_APP_ID, "1298773049344000",
				DMoneyConstants.MERCH_CODE, "9932", "business_type", "BuyGoods", DMoneyConstants.MERCH_ORDER_ID,
				"154355036379930", DMoneyConstants.NOTIFY_URL, "https://payment.d-money.dj",
				DMoneyConstants.TIMEOUT_EXPRESS, "120m", DMoneyConstants.TITLE, "iPhone16",
				DMoneyConstants.TOTAL_AMOUNT, "2000", DMoneyConstants.TRADE_TYPE, DMoneyConstants.CHECKOUT,
				DMoneyConstants.TRANS_CURRENCY, "DJF");

		Map<String, Object> requestPayload = new HashMap<>();
		requestPayload.put(DMoneyConstants.METHOD, DMoneyConstants.PAYMENT_PRE_ORDER);
		requestPayload.put(DMoneyConstants.VERSION, DMoneyConstants.VERSION_VAL);
		requestPayload.put(DMoneyConstants.TIMESTAMP, "1737530700");
		requestPayload.put(DMoneyConstants.NONCE_STR, "fcab0d2949e64a69a212aa83eab6ee1d");

		Map<String, Object> mergedParams = new HashMap<>(requestPayload);
		bizContent.forEach(mergedParams::put);

		return mergedParams;
	}

	private static Map<String, Object> createCheckoutUrl() {
		Map<String, Object> checkoutParams = new HashMap<>();
		System.err.println(System.currentTimeMillis() / 100);
		checkoutParams.put(DMoneyConstants.MERCH_APP_ID, "1298773049344000");
		checkoutParams.put(DMoneyConstants.NONCE_STR, "78f13b859b9d4cb984362e76480c400d");
		checkoutParams.put(DMoneyConstants.PREPAY_ID, "002d960a2984a07c7971b6cb8b0915628b3001");
		checkoutParams.put(DMoneyConstants.MERCH_CODE, "9932");
		checkoutParams.put(DMoneyConstants.TIMESTAMP, "1728733840");

		return checkoutParams;
	}

	private static Map<String, Object> createQueryOrder() {
		Map<String, Object> bizContent = Map.of(DMoneyConstants.MERCH_APP_ID, "1298773049344000",
				DMoneyConstants.MERCH_CODE, "9932", DMoneyConstants.MERCH_ORDER_ID, "154355036379918");

		Map<String, Object> requestPayload = new HashMap<>();
		requestPayload.put(DMoneyConstants.METHOD, DMoneyConstants.PAYMENT_QRY_ORDER);
		requestPayload.put(DMoneyConstants.VERSION, DMoneyConstants.VERSION_VAL);
		requestPayload.put(DMoneyConstants.TIMESTAMP, "1737530700");
		requestPayload.put(DMoneyConstants.NONCE_STR, "ad814bd09b4c47c5acf08db7691fa580");

		Map<String, Object> mergedParams = new HashMap<>(requestPayload);
		bizContent.forEach(mergedParams::put);

		return mergedParams;
	}

	public static void main(String[] args) {
		String privateKey = "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";

		Map<String, Object> orderParams = createOrderMap();
		Map<String, Object> checkoutParams = createCheckoutUrl();
		Map<String, Object> queryParams = createQueryOrder();

		String signature = DMoneyUtils.generateSignature(orderParams, privateKey);
		System.out.println(signature);

		try {
			boolean isVerify = verifySignature(DMoneyUtils.createRawRequest(orderParams), signature);
			System.err.println(isVerify);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static boolean verifySignature(String data, String signature) throws Exception {
		String publicKey = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAzFZQycwdAehyWiH5xLtbcsHMiX79Gu6VJQRx7WNNYVyfU+fcJukpUvsC1wrKXzrIa+4WcMRZsAVWAKcoqsN/SeRG6WYbdjX/WL5Y3YBEhLFRwrol2DzELxwc8i67MLqkA5QgxBUSKJIFQUKXnO0Im8ud3917+UUzl4HhjWUlayAwQawavxRORKYViAj6gZQI/HCRs0wOChd1VdAWoMdM2fIE3AFMUzCE/1l6TrmMgrRlOPWeSZxh9rZaBKS5H4C/vhtCDJt8d3XsqKrSuBH7At9VwP6ibGfMNzYgxMhraRHrrGdnb/h3H0ZL34TnOkI6hdPEGW5MPeWQWg8+0yoFMTNWkgICcgkesAl/kx3PDZXx4bHP4OBMM1f1VOyqj16c9GmiV2zaq2A2VOLTJZlLvWqzWy59AfGdZqNG/MHZp88Yz9XcyCBvo2LbTb4H6IhsTg3eC6CVbNWwJKEcxpFohOcR82xfKaEwph8cpJJsQHirYGSKlzx+BhFVkhqAm44RAgMBAAE=";
		byte[] keyBytes = Base64.getDecoder().decode(publicKey);
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PublicKey key = keyFactory.generatePublic(keySpec);

		Signature sig = Signature.getInstance("RSASSA-PSS");
		sig.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
		sig.initVerify(key);
		sig.update(data.getBytes(StandardCharsets.UTF_8));
		return sig.verify(Base64.getDecoder().decode(signature));
	}
}
