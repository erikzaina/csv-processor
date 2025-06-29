package br.csvProcessing.csvProcessing.repository;

import br.csvProcessing.csvProcessing.entity.CsvInfo;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CsvRepository extends JpaRepository<CsvInfo, Integer> {

    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE csv_info", nativeQuery = true)
    void truncateTable();

}
