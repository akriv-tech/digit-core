package org.egov.pg.service.gateways.dmoney;

import java.time.format.DateTimeFormatter;

public class DMoneyConstants {

	public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	public static final String GATEWAY_NAME = "D-MONEY";
	public static final String APP_SECRET = "appSecret";
	public static final String MERCH_APP_ID = "appid";
	public static final String MERCH_CODE = "merch_code";
	public static final String MERCH_ORDER_ID = "merch_order_id";
	public static final String AUTHORIZATION = "Authorization";
	public static final String NONCE_STR = "nonce_str";
	public static final String PREPAY_ID = "prepay_id";
	public static final String TIMESTAMP = "timestamp";
	public static final String BIZ_CONTENT = "biz_content";
	public static final String SIGN = "sign";
	public static final String SIGN_TYPE = "sign_type";
	public static final String X_APP_KEY = "X-APP-KEY";
	public static final String NOTIFY_URL = "notify_url";
	public static final String TIMEOUT_EXPRESS = "timeout_express";
	public static final String TOTAL_AMOUNT = "total_amount";
	public static final String TITLE = "title";
	public static final String TRADE_TYPE = "trade_type";
	public static final String TRANS_CURRENCY = "trans_currency";
	public static final String METHOD = "method";
	public static final String VERSION = "version";
	public static final String PAYMENT_ORDER_ID = "payment_order_id";
	public static final String TOKEN = "token";
	public static final String EFFECTIVE_DATE = "effectiveDate";
	public static final String EXPIRATION_DATE = "expirationDate";
	public static final String ORDER_STATUS = "order_status";
	public static final String LANGUAGE = "language";
	public static final String REFERER = "Referer";

	public static final String PAY_SUCCESS = "PAY_SUCCESS";
	public static final String PAY_PENDING = "PAY_PENDING";
	public static final String PAY_FAILED = "PAY_FAILED";

	public static final String SIGN_ALGORITHM = "SHA256withRSA";
	public static final String PAYMENT_PRE_ORDER = "payment.preorder";
	public static final String PAYMENT_QRY_ORDER = "payment.queryorder";
	public static final String VERSION_VAL = "1.0";
	public static final String CHECKOUT = "Checkout";

	private DMoneyConstants() {
	}
}
