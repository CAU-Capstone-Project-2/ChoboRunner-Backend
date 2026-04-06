// File: `src/main/java/capstone2/server/repositories/UserRepository.java`
package capstone2.server.repositories;

import capstone2.server.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
