package pe.axiz.payments.application;

import java.util.Map;

public interface PaymentServices {
  Map<String, Object> call(String operation, Map<String, Object> request);
}
