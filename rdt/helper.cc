#include "helper.h"

bool between(seq_nr a, seq_nr b, seq_nr c) {
    if ((a <= b && b < c) || (a > c && b < c) || (a > c && b >= a)) 
        return true;
    else
        return false;
}

unsigned short compute_checksum(struct frame *f) {

    f->checksum = 0;

    unsigned short *p = (unsigned short *)f;
    unsigned int res = 0;
    unsigned short *end = (unsigned short *)((char*)p + sizeof(struct frame));
    while (p != end) {
        res += *p;
        p++;
    }
    while(res >= (1 << 16)) {
        res = (res & 0xffff) + ((res >> 16) & 0xffff); 
    }
    return ~res;
}

bool verify_checksum(struct frame *f) {
    unsigned int res = 0;
    unsigned short *p = (unsigned short *)f;
    unsigned short *end = (unsigned short *)((char *)p + sizeof(struct frame));
    while (p != end) {
        res += *p;
        p++;
    }
    while (res >= (1 << 16)) {
        res = (res & 0xffff) + ((res >> 16) & 0xffff); 
    }
    return res == 0xffff;
}

void inc(seq_nr *n) {
    if (*n < MAX_SEQ) {
        (*n)++;
    } else *n = 0;
}
