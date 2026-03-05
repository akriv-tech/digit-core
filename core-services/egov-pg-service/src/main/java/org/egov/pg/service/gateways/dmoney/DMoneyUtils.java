package org.egov.pg.service.gateways.dmoney;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.egov.tracer.model.CustomException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DMoneyUtils {

	private DMoneyUtils() {
	}

	// Load private key from string
	public static PrivateKey loadPrivateKey(String privateKeyStr) {
		try {
			byte[] keyBytes = Base64.getDecoder().decode(privateKeyStr);
			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePrivate(keySpec);

		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			log.error("D-Money private key generation failed");
			throw new CustomException("PRIVATE_KEY_GEN_FAILED",
					"D-Money private key generation failed, gateway redirect URI cannot be generated");
		}
	}

	// Generate the SHA256WithRSA signature
	public static String generateSignature(Map<String, Object> params, String privateKeyStr) {
		try {
			String rawRequest = createRawRequest(params);
			Signature signature = Signature.getInstance("RSASSA-PSS");
			signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
			signature.initSign(loadPrivateKey(privateKeyStr));
			signature.update(rawRequest.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(signature.sign());

		} catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException
				| InvalidAlgorithmParameterException e) {
			log.error("Error occurred while generating signature for params " + params.toString(), e);
			throw new CustomException("CHECKSUM_GEN_FAILED",
					"Hash generation failed, gateway redirect URI " + "cannot be generated");
		}
	}

	public static String createRawRequest(Map<String, Object> params) {
		Map<String, Object> sortedParams = new TreeMap<>(params);

		return sortedParams.entrySet().stream()
				.filter(entry -> entry.getValue() != null && !entry.getKey().equals(DMoneyConstants.SIGN)
						&& !entry.getKey().equals(DMoneyConstants.SIGN_TYPE))
				.sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(Collectors.joining("&"));
	}
}
