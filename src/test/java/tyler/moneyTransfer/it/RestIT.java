package tyler.moneyTransfer.it;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import tyler.moneyTransfer.model.Account;
import tyler.moneyTransfer.model.Transfer;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Integration test checks atomicity of transfer/deposit/withdraw operations between different accounts.<br/>
 * Account states should be the same at the beginning and at the end of methods
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
public class RestIT {
    private static ExecutorService executor;
    private static ObjectMapper mapper;

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().muteForSuccessfulTests();
    @Autowired
    private MockMvc mockMvc;

    @BeforeClass
    public static void init() {
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @AfterClass
    public static void tearDown() throws Exception {
        executor.shutdown();
        executor.awaitTermination(1, MINUTES);
    }

    @Test
    public void putDepositAndWithdrawShouldPerformAtomicActions() throws Exception {
        // before
        checkStateAccounts();
        LongAdder count = new LongAdder();

        // make transfers
        executor.invokeAll(List.of(() -> {
            performDepositAndWithdraw(1001L, 1002L, new BigDecimal("11.03"), count);// 1001L -> 1002L
            return null;
        }, () -> {
            performDepositAndWithdraw(1002L, 1001L, new BigDecimal("11.03"), count);// 1002L -> 1001L
            return null;
        }, () -> {
            performDepositAndWithdraw(1001L, 1003L, new BigDecimal("11.03"), count);// 1001L -> 1003L
            return null;
        }, () -> {
            performDepositAndWithdraw(1003L, 1001L, new BigDecimal("11.03"), count);// 1003L -> 1001L
            return null;
        }, () -> {
            performDepositAndWithdraw(1002L, 1003L, new BigDecimal("11.03"), count);// 1002L -> 1003L
            return null;
        }, (Callable<Void>) () -> {
            performDepositAndWithdraw(1003L, 1002L, new BigDecimal("11.03"), count);// 1003L -> 1002L
            return null;
        }));

        // after
        checkStateAccounts();
        assertThat(count.sum()).isEqualTo(6);
    }

    @Test
    public void putTransferShouldPerformAtomicActions() throws Exception {
        LongAdder success = new LongAdder();

        // before
        checkStateAccounts();

        // make transfers
        executor.invokeAll(List.of(() -> {
            performTransfer(1001L, 1002L, new BigDecimal("10.33"), success);// 1001L -> 1002L
            return null;
        }, () -> {
            performTransfer(1002L, 1001L, new BigDecimal("10.33"), success);// 1002L -> 1001L
            return null;
        }, () -> {
            performTransfer(1001L, 1003L, new BigDecimal("10.33"), success);// 1001L -> 1003L
            return null;
        }, () -> {
            performTransfer(1003L, 1001L, new BigDecimal("10.33"), success);// 1003L -> 1001L
            return null;
        }, () -> {
            performTransfer(1002L, 1003L, new BigDecimal("10.33"), success);// 1002L -> 1003L
            return null;
        }, (Callable<Void>) () -> {
            performTransfer(1003L, 1002L, new BigDecimal("10.33"), success);// 1003L -> 1002L
            return null;
        }));

        // after
        checkStateAccounts();
        assertThat(success.sum()).isEqualTo(6);
    }

    private void checkStateAccounts() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/accounts")
                .contentType(APPLICATION_JSON))
                .andReturn().getResponse();
        List<Account> accounts = mapper.readValue(response.getContentAsString(), new TypeReference<List<Account>>() {
        });
        accounts.forEach(a -> {
            if (a.getAccNumber() == 1001L) assertThat(a.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
            if (a.getAccNumber() == 1002L) assertThat(a.getAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
            if (a.getAccNumber() == 1003L) assertThat(a.getAmount()).isEqualByComparingTo(new BigDecimal("3000.00"));
        });
    }

    private void performTransfer(long from, long to, BigDecimal amount, LongAdder count) throws Exception {
        String json = mapper.writer().writeValueAsString(Transfer.builder().from(from).to(to).amount(amount).build());
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(put("/api/accounts/transfer").contentType(APPLICATION_JSON).content(json))
                    .andReturn().getResponse();
        }
        count.increment();
    }

    private void performDepositAndWithdraw(long withdrawAcc, long depositAcc, BigDecimal amount, LongAdder count) throws Exception {
        String depositJson = mapper.writer().writeValueAsString(Transfer.builder().to(depositAcc).amount(amount).build());
        String withdrawJson = mapper.writer().writeValueAsString(Transfer.builder().from(withdrawAcc).amount(amount).build());
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(put("/api/account/deposit").contentType(APPLICATION_JSON).content(depositJson))
                    .andReturn().getResponse();
            mockMvc.perform(put("/api/account/withdraw").contentType(APPLICATION_JSON).content(withdrawJson))
                    .andReturn().getResponse();
        }
        count.increment();
    }
}
