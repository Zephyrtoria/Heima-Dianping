package github.zephyrtoria.hmdp;

import com.github.zephyrtoria.hmdp.service.IShopService;
import com.github.zephyrtoria.hmdp.service.impl.ShopServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmdpApplicationTests {

	@Resource
	private IShopService shopService;

}
