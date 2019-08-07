package tyler.moneyTransfer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static javax.persistence.GenerationType.IDENTITY;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Entity
public class Log {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @JsonIgnore
    private Long id;

    @JsonInclude(NON_NULL)
    private Long fromNumber;

    @JsonInclude(NON_NULL)
    private Long toNumber;

    @PositiveOrZero
    private BigDecimal amount;

    private String description;

    @Generated(GenerationTime.INSERT)
    @Basic
    private Instant date;
}
