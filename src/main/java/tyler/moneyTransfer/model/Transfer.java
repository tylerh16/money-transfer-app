package tyler.moneyTransfer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Transfer {
    @Nullable
    private Long from;
    @Nullable
    private Long to;
    private BigDecimal amount;
}
