package capstone2.server.services.rag;

/**
 * Pinecone Query 결과 1건 — 메타데이터에 동봉된 chunk 본문(text)과 식별 정보.
 * 설계 문서 §4.3 의 메타데이터 스키마를 그대로 따른다.
 */
public record RagChunk(
        String id,
        double score,
        String text,
        String category,
        String tone,
        String tier,
        String title,
        String doi,
        String sourceUrl
) {
}
