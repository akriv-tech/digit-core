package org.egov.pg.service.gateways.dmoney;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.egov.pg.models.Transaction;
import org.egov.pg.service.Gateway;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ServiceCallException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * D-Money Gateway implementation
 */
@Component
@Slf4j
public class DMoneyGateway implements Gateway {

	private final boolean isActive;
	private final String appId;
	private final String appSecret;
	private final String merchCode;
	private final String xAppKey;
	private final String privateKey;
	private final String dmoneyHost;
	private final String tokenUrl;
	private final String preOrderUrl;
	private final String statusUrl;
	private final String checkoutUrl;
	private final String transCurrency;
	private final String timeoutExpress;
	private final String language;
	private final String referer;

	private final RestTemplate restTemplate;
	private String accessToken;
	private String effectiveDateStr;
	private String expirationDateStr;

	@Autowired
	public DMoneyGateway(RestTemplate restTemplate, Environment environment, ObjectMapper objectMapper) {
		this.restTemplate = restTemplate;

		isActive = Boolean.valueOf(environment.getRequiredProperty("dmoney.active"));
		appId = environment.getRequiredProperty("dmoney.app.id");
		appSecret = environment.getRequiredProperty("dmoney.app.secret");
		merchCode = environment.getRequiredProperty("dmoney.merchant.code");
		xAppKey = environment.getRequiredProperty("dmoney.x.app.key");
		privateKey = environment.getRequiredProperty("dmoney.private.key");
		dmoneyHost = environment.getRequiredProperty("dmoney.host");
		tokenUrl = environment.getRequiredProperty("dmoney.token.url");
		preOrderUrl = environment.getRequiredProperty("dmoney.preorder.url");
		statusUrl = environment.getRequiredProperty("dmoney.status.url");
		checkoutUrl = environment.getRequiredProperty("dmoney.checkout.url");
		transCurrency = environment.getRequiredProperty("dmoney.trans.currency");
		timeoutExpress = environment.getRequiredProperty("dmoney.timeout.express");
		language = environment.getRequiredProperty("dmoney.language");
		referer = environment.getRequiredProperty("dmoney.referer");
	}

	@SuppressWarnings({ "unchecked" })
	@Override
	public URI generateRedirectURI(Transaction transaction) {
		ensureAccessToken();
		Map<String, Object> orderResponse = createPreOrder(transaction);
		if (orderResponse == null || orderResponse.isEmpty()) {
			throw new CustomException("PRE_ORDER_FAILED",
					"The merchant failed to call the payment platform to place an order.");
		}

		Map<String, Object> bizContent = (Map<String, Object>) orderResponse.get(DMoneyConstants.BIZ_CONTENT);
		Object prepayId = bizContent.get(DMoneyConstants.PREPAY_ID);
		if (prepayId == null) {
			throw new CustomException("PRE_ORDER_FAILED",
					String.valueOf(orderResponse.getOrDefault("msg", "Unknown error occurred.")));
		}

		log.info("nonce_str: " + orderResponse.get(DMoneyConstants.NONCE_STR));
		log.info("prepay_id: " + bizContent.get(DMoneyConstants.PREPAY_ID));

		Map<String, Object> signParams = Map.of(DMoneyConstants.MERCH_APP_ID, appId, DMoneyConstants.NONCE_STR,
				String.valueOf(orderResponse.get(DMoneyConstants.NONCE_STR)), DMoneyConstants.PREPAY_ID,
				String.valueOf(bizContent.get(DMoneyConstants.PREPAY_ID)), DMoneyConstants.MERCH_CODE, merchCode,
				DMoneyConstants.TIMESTAMP, String.valueOf(System.currentTimeMillis() / 1000));

		log.info("Sign parameters of checkout url: " + signParams);

		String sign = DMoneyUtils.generateSignature(signParams, privateKey);
		Map<String, Object> checkoutParams = new TreeMap<>(signParams);
		checkoutParams.put(DMoneyConstants.SIGN, sign);
		checkoutParams.put(DMoneyConstants.SIGN_TYPE, DMoneyConstants.SIGN_ALGORITHM);
		checkoutParams.put(DMoneyConstants.VERSION, DMoneyConstants.VERSION_VAL);
		checkoutParams.put(DMoneyConstants.TRADE_TYPE, DMoneyConstants.CHECKOUT);
		checkoutParams.put(DMoneyConstants.LANGUAGE, language);
		checkoutParams.put(DMoneyConstants.REFERER, referer);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		checkoutParams.forEach((key, value) -> params.put(key, List.of(value.toString())));
		UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(checkoutUrl).queryParams(params).build();

		log.info("Checkout url : " + uriComponents.toString());

		return uriComponents.encode().toUri();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map<String, Object> createPreOrder(Transaction transaction) {
		ensureAccessToken();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(DMoneyConstants.AUTHORIZATION, accessToken);
		headers.set(DMoneyConstants.X_APP_KEY, xAppKey);

		Map<String, String> bizContent = Map.of(DMoneyConstants.MERCH_APP_ID, appId, DMoneyConstants.MERCH_CODE,
				merchCode, DMoneyConstants.MERCH_ORDER_ID, transaction.getTxnId().replace("_", ""),
				DMoneyConstants.NOTIFY_URL, transaction.getCallbackUrl(), DMoneyConstants.TIMEOUT_EXPRESS,
				timeoutExpress, DMoneyConstants.TITLE, transaction.getModule(), DMoneyConstants.TOTAL_AMOUNT,
				transaction.getTxnAmount(), DMoneyConstants.TRADE_TYPE, DMoneyConstants.CHECKOUT,
				DMoneyConstants.TRANS_CURRENCY, transCurrency);

		Map<String, Object> requestBody = createRequestBody(bizContent, DMoneyConstants.PAYMENT_PRE_ORDER);

		HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
		ResponseEntity<Map> response = restTemplate.postForEntity(dmoneyHost + preOrderUrl, request, Map.class);

		log.info("Pre-order response: " + response);

		return response.getBody();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Transaction fetchStatus(Transaction currentStatus, Map<String, String> params) {
		ensureAccessToken();
		HttpHeaders headers = createHeaders();

		Map<String, String> bizContent = Map.of(DMoneyConstants.MERCH_APP_ID, appId, DMoneyConstants.MERCH_CODE,
				merchCode, DMoneyConstants.MERCH_ORDER_ID, currentStatus.getTxnId().replace("_", ""));

		Map<String, Object> requestBody = createRequestBody(bizContent, DMoneyConstants.PAYMENT_QRY_ORDER);

		HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);
		ResponseEntity<Map> response = restTemplate.postForEntity(dmoneyHost + statusUrl, httpEntity, Map.class);

		log.info("Fetch status response: " + response);

		return transformRawResponse(response.getBody(), currentStatus);
	}

	@Override
	public boolean isActive() {
		return isActive;
	}

	@Override
	public String gatewayName() {
		return DMoneyConstants.GATEWAY_NAME;
	}

	@Override
	public String transactionIdKeyInResponse() {
		return DMoneyConstants.MERCH_ORDER_ID;
	}

	private void ensureAccessToken() {
		if (StringUtils.isBlank(accessToken) || !isTokenActive()) {
			fetchAccessToken();
		}
	}

	private boolean isTokenActive() {
		if (StringUtils.isBlank(accessToken)) {
			return false;
		}

		LocalDateTime now = LocalDateTime.now();
		log.info("Current datetime: " + now);

		LocalDateTime effectiveDate = LocalDateTime.parse(effectiveDateStr, DMoneyConstants.DATE_FORMATTER);
		LocalDateTime expirationDate = LocalDateTime.parse(expirationDateStr, DMoneyConstants.DATE_FORMATTER);

		return (now.isEqual(effectiveDate) || now.isAfter(effectiveDate)) && now.isBefore(expirationDate);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void fetchAccessToken() {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.set(DMoneyConstants.X_APP_KEY, xAppKey);

			Map<String, String> map = new HashMap<>();
			map.put(DMoneyConstants.APP_SECRET, appSecret);

			HttpEntity<Map<String, String>> request = new HttpEntity<>(map, headers);
			ResponseEntity<Map> response = restTemplate.postForEntity(dmoneyHost + tokenUrl, request, Map.class);

			if (response.getStatusCode() == HttpStatus.OK) {
				Map<String, String> responseMap = response.getBody();
				accessToken = responseMap.get(DMoneyConstants.TOKEN);
				log.info("Access token: " + accessToken);

				effectiveDateStr = responseMap.get(DMoneyConstants.EFFECTIVE_DATE);
				log.info("Effective datetime: " + effectiveDateStr);

				expirationDateStr = responseMap.get(DMoneyConstants.EXPIRATION_DATE);
				log.info("Expiration datetime: " + expirationDateStr);
			} else {
				throw new CustomException("TOKEN_GEN_ERROR", String.valueOf(response.getBody().get("errorMsg")));
			}
		} catch (RestClientException e) {
			log.error("D-Money fetching access token failed", e);
			throw new ServiceCallException("Error occurred while fetching access token from D-Money");
		}
	}

	private HttpHeaders createHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(DMoneyConstants.AUTHORIZATION, accessToken);
		headers.set(DMoneyConstants.X_APP_KEY, xAppKey);
		return headers;
	}

	private Map<String, Object> createRequestBody(Map<String, String> bizContent, String orderType) {
		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put(DMoneyConstants.METHOD, orderType);
		requestBody.put(DMoneyConstants.VERSION, DMoneyConstants.VERSION_VAL);
		requestBody.put(DMoneyConstants.TIMESTAMP, String.valueOf(System.currentTimeMillis() / 1000));
		requestBody.put(DMoneyConstants.NONCE_STR, UUID.randomUUID().toString().replace("-", ""));

		Map<String, Object> params = new HashMap<>(requestBody);
		bizContent.forEach(params::put);

		log.info("Sign parameters: " + bizContent);

		requestBody.put(DMoneyConstants.SIGN, DMoneyUtils.generateSignature(params, privateKey));
		requestBody.put(DMoneyConstants.SIGN_TYPE, DMoneyConstants.SIGN_ALGORITHM);
		requestBody.put(DMoneyConstants.BIZ_CONTENT, bizContent);

		return requestBody;
	}

	@SuppressWarnings("unchecked")
	private Transaction transformRawResponse(Map<String, Object> response, Transaction currentStatus) {
		if (response == null) {
			throw new CustomException("STATUS_FETCH_FAILED", "Error occured while fetching the status of payment");
		}

		Map<String, Object> bizContent = (Map<String, Object>) response.get(DMoneyConstants.BIZ_CONTENT);
		Object orderStatusObj = bizContent.get(DMoneyConstants.ORDER_STATUS);
		if (orderStatusObj == null) {
			throw new CustomException("QUERY_ORDER_FAILED",
					String.valueOf(response.getOrDefault("msg", "Unknown error occurred.")));
		}

		String orderStatus = String.valueOf(orderStatusObj);
		log.info("Payment order status: " + orderStatus);

		Transaction.TxnStatusEnum status;
		String gatewayStatusMsg;

		switch (orderStatus) {
		case DMoneyConstants.PAY_SUCCESS:
			status = Transaction.TxnStatusEnum.SUCCESS;
			gatewayStatusMsg = DMoneyConstants.PAY_SUCCESS;
			break;
		case DMoneyConstants.PAY_PENDING:
			status = Transaction.TxnStatusEnum.PENDING;
			gatewayStatusMsg = DMoneyConstants.PAY_PENDING;
			break;
		default:
			status = Transaction.TxnStatusEnum.FAILURE;
			gatewayStatusMsg = DMoneyConstants.PAY_FAILED;
			break;
		}

		return Transaction.builder().txnId(currentStatus.getTxnId())
				.txnAmount(String.valueOf(bizContent.get(DMoneyConstants.TOTAL_AMOUNT))).txnStatus(status)
				.gatewayTxnId(String.valueOf(bizContent.get(DMoneyConstants.PAYMENT_ORDER_ID)))
				.gatewayStatusMsg(gatewayStatusMsg).responseJson(response).build();
	}
}
