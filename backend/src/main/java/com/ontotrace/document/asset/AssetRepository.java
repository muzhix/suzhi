package com.ontotrace.document.asset;

import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

/**
 * 资产仓储。
 *
 * @author hanbd
 */
public interface AssetRepository extends ListCrudRepository<Asset, UUID> {}
