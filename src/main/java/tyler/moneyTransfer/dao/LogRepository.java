package tyler.moneyTransfer.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tyler.moneyTransfer.model.Log;

@Repository
public interface LogRepository extends JpaRepository<Log, Long> {

}
